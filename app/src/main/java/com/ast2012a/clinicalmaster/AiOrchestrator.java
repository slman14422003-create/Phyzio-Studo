package com.ast2012a.clinicalmaster;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class AiOrchestrator {
    private AiOrchestrator() {}

    public interface ResultCallback {
        void onChatReply(String reply);

        void onGroundedReply(String reply, String sourceLabel, String sourceUrl);
        void onError(String message);
    }

    public interface StageListener {
        void onClassifying();

        void onSearching();

        void onThinking();
    }

    public static final class Options {
        public boolean deep;

        public Options(boolean deep) {
            this.deep = deep;
        }
    }

    public static void answer(Context ctx, String text, StageListener stages, ResultCallback callback) {
        answer(ctx, text, null, null, stages, callback);
    }

    public static void answer(Context ctx, String text, String patientId,
            StageListener stages, ResultCallback callback) {
        answer(ctx, text, patientId, null, stages, callback);
    }

    public static void answer(Context ctx, String text, String patientId,
            List<ChatMessage> history, StageListener stages, ResultCallback callback) {
        answer(ctx, text, patientId, null, null, history, stages, callback);
    }

    public static void answer(Context ctx, String text, String patientId,
            String documentName, String documentText,
            List<ChatMessage> history, StageListener stages, ResultCallback callback) {
        answer(ctx, text, patientId, documentName, documentText, history, null, stages, callback);
    }

    public static void answer(Context ctx, String text, String patientId,
            String documentName, String documentText,
            List<ChatMessage> history, Options options, StageListener stages, ResultCallback callback) {
        if (stages != null) stages.onClassifying();
        final String historyContext = AiPrompts.buildHistoryContext(history);
        final boolean forceDeep = options != null && options.deep;

        if (documentText != null && !documentText.trim().isEmpty()) {
            AiIntent docIntent = new AiIntent(AiIntent.ROUTE_SEARCH,
                    forceDeep ? AiIntent.DEPTH_DEEP : AiIntent.DEPTH_STANDARD, null, null);
            runGrounded(ctx, text, patientId, historyContext, docIntent, documentName, documentText, stages, callback);
            return;
        }

        AiClient.classifyIntent(text, historyContext, new AiClient.Callback() {
            @Override
            public void onSuccess(String decision) {
                AiIntent intent = AiIntent.parse(decision, text);
                if (intent.isChat() && !forceDeep) {
                    runChat(ctx, text, historyContext, stages, callback);
                    return;
                }
                if (forceDeep) {
                    intent = new AiIntent(AiIntent.ROUTE_SEARCH, AiIntent.DEPTH_DEEP,
                            intent.englishTerm, intent.standaloneQuery);
                }
                runGrounded(ctx, text, patientId, historyContext, intent, null, null, stages, callback);
            }

            @Override
            public void onError(String message) {
                AiIntent fallback = forceDeep
                        ? new AiIntent(AiIntent.ROUTE_SEARCH, AiIntent.DEPTH_DEEP, null, text)
                        : AiIntent.fallback(text);
                runGrounded(ctx, text, patientId, historyContext, fallback, null, null, stages, callback);
            }
        });
    }

    public static void digestConversation(Context ctx, List<ChatMessage> history,
            StageListener stages, ResultCallback callback) {
        if (stages != null) stages.onThinking();
        String transcript = buildTranscript(history);
        if (transcript.isEmpty()) {
            callback.onError(ctx.getString(R.string.ai_digest_empty));
            return;
        }
        String system = AiBrain.DIGEST + "\n\nCONVERSATION:\n" + transcript;
        sendWithAutoContinue(system, "Consolidate the conversation above into key points.", "digest", "",
                AiModelSelector.forClinical(), 0, new AiClient.Callback() {
            @Override
            public void onSuccess(String reply) {
                callback.onGroundedReply(reply, ctx.getString(R.string.ai_source_digest), null);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static String buildTranscript(List<ChatMessage> history) {
        if (history == null) return "";
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 40);
        for (int i = start; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            if (m == null || m.text == null || m.text.trim().isEmpty()) continue;
            boolean user = m.role == ChatMessage.ROLE_USER;
            String body = user ? m.text.trim() : MarkdownLite.toPlain(m.text);
            int max = user ? 700 : 1500;
            if (body.length() > max) body = body.substring(0, max) + "...";
            sb.append(user ? "Physician: " : "Assistant: ").append(body).append("\n\n");
        }
        return sb.toString().trim();
    }

    private static void runChat(Context ctx, String text, String historyContext,
            StageListener stages, ResultCallback callback) {
        if (stages != null) stages.onThinking();
        String chatModel = AiModelSelector.forChat(text, historyContext != null);
        sendWithAutoContinue(AiPrompts.buildChatSystemPrompt(ctx, historyContext), text, text, "",
                chatModel, 0, new AiClient.Callback() {
            @Override public void onSuccess(String reply) { callback.onChatReply(reply); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    private static final int MAX_CONTINUATIONS = 6;

    private static String finalizeReply(String text) {
        if (text == null || text.trim().isEmpty()) return text;
        text = stripLeakedContinuationMeta(text);
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        StringBuilder seenSoFar = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String kept = stripDuplicateSentencesInLine(lines[i], normalizeForCompare(seenSoFar.toString()));
            if (i > 0) result.append('\n');
            result.append(kept);
            if (!kept.trim().isEmpty()) seenSoFar.append(' ').append(kept);
        }
        return result.toString().trim();
    }

    private static final String[] LEAKED_META_MARKERS = {
            "من حيث توقفت", "بدون إعادة أي جزء سابق", "لا أزال متوقف", "ما زلت متوقف",
            "سأكمل الإجابة", "سأكمّل الإجابة", "سأكمل من", "سأكمّل من", "هذا استكمال لرد سابق",
            "آخر جزء فعلي من الرد السابق", "المقطع المقتبس", "كما طلبت أعلاه", "بناءً على طلبك بالاستكمال"
    };

    private static String stripLeakedContinuationMeta(String text) {
        if (text == null || text.trim().isEmpty()) return text;
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) result.append('\n');
            if (line.trim().isEmpty() || NUMBERED_LIST_ITEM.matcher(line).matches()) {
                result.append(line);
                continue;
            }
            String[] sentences = line.split("(?<=[.!؟?])\\s+");
            StringBuilder kept = new StringBuilder();
            for (String sentence : sentences) {
                if (sentenceLeaksContinuationMeta(sentence)) continue;
                if (kept.length() > 0) kept.append(' ');
                kept.append(sentence);
            }
            result.append(kept);
        }
        return result.toString();
    }

    private static boolean sentenceLeaksContinuationMeta(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return false;
        for (String marker : LEAKED_META_MARKERS) {
            if (sentence.contains(marker)) return true;
        }
        return false;
    }

    private static void sendWithAutoContinue(String systemContext, String userMessage,
            String originalQuestion, String accumulated, String model, int attempt, AiClient.Callback finalCallback) {
        sendWithAutoContinue(systemContext, userMessage, originalQuestion, accumulated, model, null, null, attempt, finalCallback);
    }

    private static void sendWithAutoContinue(String systemContext, String userMessage,
            String originalQuestion, String accumulated, String model, String firstChunk, String contextTailGiven,
            int attempt, AiClient.Callback finalCallback) {
        AiClient.sendMessage(systemContext, userMessage, model, new AiClient.Callback() {
            @Override
            public void onSuccess(String reply) {
                String effectiveFirstChunk = firstChunk != null ? firstChunk : reply;

                if (attempt > 0 && looksLikeDuplicateRestart(effectiveFirstChunk, reply)) {
                    finalCallback.onSuccess(finalizeReply(accumulated));
                    return;
                }

                String newContent = reply;
                if (attempt > 0 && contextTailGiven != null) {
                    int overlapRawEnd = findEchoOverlapRawLength(contextTailGiven, reply);
                    if (overlapRawEnd > 0) {
                        newContent = reply.substring(overlapRawEnd).trim();
                    }
                }

                if (attempt > 0) {
                    newContent = stripAlreadyCoveredContent(accumulated, newContent).trim();
                }

                if (attempt > 0 && newContent.trim().isEmpty()) {
                    finalCallback.onSuccess(finalizeReply(accumulated));
                    return;
                }

                String combined = accumulated.isEmpty() ? newContent
                        : (newContent.isEmpty() ? accumulated : accumulated + " " + newContent);

                boolean appearsTruncated = looksTruncated(combined);
                boolean willContinue = attempt < MAX_CONTINUATIONS && appearsTruncated;

                if (willContinue) {
                    combined = trimTrailingPartialWord(combined);
                }

                if (willContinue) {
                    String tailForNext = lastCharsAtWordBoundary(combined, 700);

                    String continueSystem = systemContext + "\n\n---\nهذا استدعاء استكمال داخلي (النظام، مش " +
                            "المستخدم، هو اللي طلبه) لرد سابق على نفس السؤال الأصلي (\"" + originalQuestion +
                            "\") انقطع في المنتصف. اكتب فقط تكملة المحتوى الإكلينيكي الفعلي - ممنوع نهائيًا " +
                            "أي جملة أو عبارة تتكلم عن عملية الاستكمال نفسها (زي \"سأكمل من حيث توقفت\"، " +
                            "\"لا أزال متوقفًا عند\"، \"بدون إعادة أي جزء سابق\"، \"كما طلبت\"، أو أي إشارة " +
                            "مباشرة أو غير مباشرة لكون هذا الرد استكمالًا أو لوجود انقطاع سابق) - ابدأ " +
                            "مباشرة بأول كلمة من المحتوى الإكلينيكي الجديد نفسه، من غير أي مقدمة عن نفسه. " +
                            "ممنوع تعيد كتابة أي كلمة من المقطع المقتبس أدناه، وممنوع تعيد أي فكرة أو نقطة " +
                            "سبق ذكرها في الإجابة كلها من أولها (مش بس المقطع المقتبس) حتى لو بصياغة أو " +
                            "ترتيب مختلف - لو حسّيت إنك هتكرر فكرة سبق قولها، انتقل مباشرة للنقطة الجديدة " +
                            "اللي لسه ما اتقالتش، أو اختم ردك بصمت لو مفيش حاجة جديدة فعلًا تضيفها. آخر " +
                            "جزء فعلي من الرد السابق (للسياق فقط - لا تكرره ولا تعلّق عليه):" +
                            "\n\"\"\"\n" + tailForNext + "\n\"\"\"";
                    sendWithAutoContinue(continueSystem, "تابع كتابة الموضوع نفسه مباشرة.",
                            originalQuestion, combined, model, effectiveFirstChunk, tailForNext, attempt + 1, finalCallback);
                } else {
                    finalCallback.onSuccess(finalizeReply(combined));
                }
            }

            @Override
            public void onError(String message) {
                if (!accumulated.isEmpty()) {
                    finalCallback.onSuccess(finalizeReply(accumulated));
                } else {
                    finalCallback.onError(message);
                }
            }
        });
    }

    private static int findEchoOverlapRawLength(String contextTail, String reply) {
        if (contextTail == null || reply == null) return 0;
        String normTail = normalizeForCompare(contextTail);
        if (normTail.isEmpty() || reply.trim().isEmpty()) return 0;

        int rawScanLimit = Math.min(reply.length(), contextTail.length() + 200);
        StringBuilder normReplyStart = new StringBuilder();
        java.util.List<Integer> rawIndexAfter = new java.util.ArrayList<>();
        boolean lastWasSpace = true;
        for (int i = 0; i < rawScanLimit; i++) {
            char c = reply.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace && normReplyStart.length() > 0) {
                    normReplyStart.append(' ');
                    rawIndexAfter.add(i + 1);
                }
                lastWasSpace = true;
            } else {
                normReplyStart.append(c);
                rawIndexAfter.add(i + 1);
                lastWasSpace = false;
            }
        }
        String normStart = normReplyStart.toString();
        if (normStart.isEmpty()) return 0;

        int maxLen = Math.min(normTail.length(), normStart.length());
        for (int len = maxLen; len >= 18; len--) {
            if (normTail.regionMatches(normTail.length() - len, normStart, 0, len)) {
                return rawIndexAfter.get(len - 1);
            }
        }
        return 0;
    }

    private static boolean looksTruncated(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.length() < 200) return false;
        int fenceCount = 0;
        int idx = 0;
        while ((idx = t.indexOf("```", idx)) != -1) { fenceCount++; idx += 3; }
        if (fenceCount % 2 != 0) return true;
        char last = t.charAt(t.length() - 1);
        if (CLEAR_ENDING_CHARS.indexOf(last) >= 0) return false;
        if (last == '*' || last == '|') return false;
        String lastLine = t.substring(t.lastIndexOf('\n') + 1).trim();
        if (lastLine.startsWith("#") || lastLine.startsWith("|")) return false;
        boolean listLine = lastLine.matches("^(?:[-*\u2022]|\\d{1,3}[.)])\\s.*");
        if (listLine && lastLine.length() <= 200) return false;
        return true;
    }

    private static final String CLEAR_ENDING_CHARS =
            ".!?؟؛;)]”\"»…✅⚠️•";

    private static boolean looksLikeDuplicateRestart(String firstChunk, String newReply) {
        if (firstChunk == null || newReply == null) return false;
        String a = normalizeForCompare(firstChunk);
        String b = normalizeForCompare(newReply);
        int len = Math.min(60, Math.min(a.length(), b.length()));
        if (len < 20) return false;
        return a.substring(0, len).equals(b.substring(0, len));
    }

    private static String normalizeForCompare(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String lastChars(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static String lastCharsAtWordBoundary(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        String tail = text.substring(text.length() - max);
        int firstSpace = -1;
        for (int i = 0; i < tail.length(); i++) {
            if (Character.isWhitespace(tail.charAt(i))) { firstSpace = i; break; }
        }
        if (firstSpace >= 0 && firstSpace < tail.length() - 1) {
            return tail.substring(firstSpace + 1);
        }
        return tail;
    }

    private static String trimTrailingPartialWord(String text) {
        if (text == null || text.isEmpty()) return text;
        if (Character.isWhitespace(text.charAt(text.length() - 1))) return text;
        int searchFrom = Math.max(0, text.length() - 40);
        int lastSpace = -1;
        for (int i = text.length() - 1; i >= searchFrom; i--) {
            if (Character.isWhitespace(text.charAt(i))) { lastSpace = i; break; }
        }
        if (lastSpace < 0) return text;
        return text.substring(0, lastSpace).trim();
    }

    private static String stripAlreadyCoveredContent(String accumulated, String newContent) {
        if (newContent == null || newContent.trim().isEmpty()) return newContent;
        if (accumulated == null || accumulated.trim().isEmpty()) return newContent;
        String normAccumulated = normalizeForCompare(accumulated);

        String[] lines = newContent.split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String keptLine = stripDuplicateSentencesInLine(lines[i], normAccumulated);
            if (i > 0) result.append('\n');
            result.append(keptLine);
        }
        return result.toString();
    }

    private static final int MIN_DUPLICATE_UNIT_LEN = 18;

    private static final java.util.regex.Pattern NUMBERED_LIST_ITEM =
            java.util.regex.Pattern.compile("^\\s*\\d{1,2}[.)]\\s+.+");

    private static String stripDuplicateSentencesInLine(String line, String normAccumulated) {
        if (line == null || line.trim().isEmpty()) return line;
        String normLine = normalizeForCompare(line);
        if (normLine.length() >= MIN_DUPLICATE_UNIT_LEN && normAccumulated.contains(normLine)) {
            return "";
        }

        if (NUMBERED_LIST_ITEM.matcher(line).matches()) {
            return line;
        }
        String[] sentences = line.split("(?<=[.!؟?])\\s+");
        if (sentences.length <= 1) return line;
        StringBuilder kept = new StringBuilder();

        String seenInThisLine = "";
        for (String sentence : sentences) {
            String normSentence = normalizeForCompare(sentence);
            boolean dupFromEarlierLines = normSentence.length() >= MIN_DUPLICATE_UNIT_LEN
                    && normAccumulated.contains(normSentence);
            boolean dupWithinSameLine = normSentence.length() >= MIN_DUPLICATE_UNIT_LEN
                    && seenInThisLine.contains(normSentence);
            if (dupFromEarlierLines || dupWithinSameLine) {
                continue;
            }
            if (kept.length() > 0) kept.append(' ');
            kept.append(sentence);
            seenInThisLine = seenInThisLine.isEmpty() ? normSentence : seenInThisLine + " " + normSentence;
        }
        return kept.toString();
    }

    private static final String[] PATIENT_OVERVIEW_HINTS = {
            "مرضاي", "مرضى", "المرضى", "مريضي", "حالاتي مع", "عيادتي", "مرضى العيادة"
    };

    private static boolean mentionsPatientsGenerally(String text) {
        if (text == null) return false;
        String t = text.trim();
        for (String hint : PATIENT_OVERVIEW_HINTS) {
            if (t.contains(hint)) return true;
        }
        return false;
    }

    private static final String[] PROGRAM_OVERVIEW_HINTS = {
            "برنامجي", "برامجي", "برامج العلاج", "البرنامج العلاجي", "برامج علاجية",
            "خطة العلاج", "خططي العلاجية", "برامج التأهيل"
    };

    private static boolean mentionsProgramsGenerally(String text) {
        if (text == null) return false;
        String t = text.trim();
        for (String hint : PROGRAM_OVERVIEW_HINTS) {
            if (t.contains(hint)) return true;
        }
        return false;
    }
    private static final class Gathered {
        PhysiopediaClient.Result physio;
        WikipediaClient.Result wiki;
        PubMedClient.Result pubmed;
        List<PubMedXml.Article> articles = new ArrayList<>();
    }

    private static List<String> planDeepQueries(String question, String historyContext, String term) {
        final String[] holder = {null};
        StringBuilder user = new StringBuilder("Clinical question: ").append(question);
        if (term != null) user.append("\nSuggested core term: ").append(term);
        String system = AiBrain.DEEP_PLANNER;
        String history = AiBrain.historyBlock(historyContext);
        if (history != null) system = system + "\n\n" + history;
        AiClient.sendMessage(system, user.toString(), AiModelSelector.forClinical(), new AiClient.Callback() {
            @Override public void onSuccess(String reply) { holder[0] = reply; }
            @Override public void onError(String message) { holder[0] = null; }
        });
        return DeepEvidence.parsePlan(holder[0], term != null ? term : question);
    }

    private static List<PubMedXml.Article> searchPubMedParallel(List<String> queries) {
        List<List<PubMedXml.Article>> groups = new ArrayList<>();
        if (queries.isEmpty()) return new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(queries.size());
        try {
            List<Future<List<PubMedXml.Article>>> futures = new ArrayList<>();
            for (final String q : queries) {
                futures.add(pool.submit(new Callable<List<PubMedXml.Article>>() {
                    @Override
                    public List<PubMedXml.Article> call() {
                        return PubMedClient.searchMany(q, DeepEvidence.PER_QUERY);
                    }
                }));
            }
            for (Future<List<PubMedXml.Article>> f : futures) {
                try {
                    groups.add(f.get(25, TimeUnit.SECONDS));
                } catch (Exception ignored) {
                    f.cancel(true);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return DeepEvidence.merge(groups, DeepEvidence.MAX_ARTICLES);
    }

    private static void runGrounded(Context ctx, String text, String patientId,
            String historyContext, AiIntent intent, String documentName, String documentText,
            StageListener stages, ResultCallback callback) {
        if (stages != null) stages.onSearching();

        final boolean hasDocument = documentText != null && !documentText.trim().isEmpty();
        final int depth = intent.depth;
        final boolean deep = depth == AiIntent.DEPTH_DEEP;
        final String query = intent.effectiveQuery(text);
        final String term = intent.effectiveTerm();

        if (!hasDocument && !deep) {
            DataManager.SearchResult direct = DataManager.search(query, DataManager.allCases(ctx));
            if (!direct.items.isEmpty() && !direct.isFallback) {
                CaseItem top = direct.items.get(0);
                String reply = DataManager.buildLocalAnswer(top);
                if (direct.items.size() > 1) {
                    reply += ctx.getString(R.string.ai_more_local_matches, direct.items.size() - 1);
                }
                callback.onGroundedReply(reply, ctx.getString(R.string.ai_source_local_instant), null);
                return;
            }
        }

        DataManager.GroundingResult grounding = DataManager.buildGroundingContext(ctx, query, 3);

        Gathered g = new Gathered();
        String externalQuery = term != null ? term : query;
        if (deep) {
            List<String> queries = planDeepQueries(query, historyContext, term);
            g.articles = searchPubMedParallel(queries);
            g.physio = PhysiopediaClient.search(externalQuery);
            g.wiki = WikipediaClient.search(query);
        } else if (depth == AiIntent.DEPTH_STANDARD) {
            g.physio = PhysiopediaClient.search(externalQuery);
            g.wiki = WikipediaClient.search(query);
            g.pubmed = PubMedClient.search(externalQuery);
        }

        StringBuilder extraContext = new StringBuilder();

        if (hasDocument) {
            extraContext.append("PDF document currently open in the in-app reader")
                    .append(documentName != null && !documentName.trim().isEmpty()
                            ? " (file name: \"" + documentName.trim() + "\")" : "")
                    .append(" - this is the primary source when the user refers to this file or document or asks "
                            + "to summarize, explain or analyze it; prefer it over other sources unless it "
                            + "contradicts an established safety rule:\n")
                    .append(documentText.trim()).append("\n\n");
        }

        if (grounding != null) {
            extraContext.append("Documented protocols relevant to the question from the device database:\n")
                    .append(grounding.contextText).append("\n\n");
        }

        CloudKnowledgeManager.syncIfNeeded(ctx);
        String cloudDocsContext = CloudKnowledgeManager.buildCloudDocumentsContext(ctx, query);
        if (cloudDocsContext != null) {
            extraContext.append(cloudDocsContext).append("\n\n");
        }

        if (patientId != null && !patientId.trim().isEmpty()) {
            String patientContext = PatientManager.buildRedactedPatientContext(ctx, patientId);
            if (patientContext != null && !patientContext.isEmpty()) {
                extraContext.append(patientContext).append("\n\n");
            }
        } else if (mentionsPatientsGenerally(text) || mentionsPatientsGenerally(query)) {
            String overview = PatientManager.buildAllPatientsOverviewContext(ctx);
            if (overview != null) {
                extraContext.append(overview).append("\n\n");
            }
        }

        if (mentionsProgramsGenerally(text) || mentionsProgramsGenerally(query)) {
            String programsOverview = TreatmentProgramManager.buildProgramsOverviewContext(ctx);
            if (programsOverview != null) {
                extraContext.append(programsOverview).append("\n\n");
            }
        }

        if (g.physio != null) {
            extraContext.append("External source 1 - Physiopedia (physiotherapy reference, article: ")
                    .append(g.physio.title).append("):\n").append(g.physio.extract).append("\n\n");
        }
        if (g.wiki != null) {
            extraContext.append("External source 2 - Wikipedia (general encyclopedia, ")
                    .append("ar".equals(g.wiki.lang) ? "Arabic edition" : "English edition")
                    .append(", article: ").append(g.wiki.title).append("):\n").append(g.wiki.extract).append("\n\n");
        }
        if (g.pubmed != null) {
            extraContext.append("External source 3 - PubMed (peer-reviewed literature, article: ")
                    .append(g.pubmed.title).append("):\n").append(g.pubmed.extract).append("\n\n");
        }
        if (!g.articles.isEmpty()) {
            extraContext.append(DeepEvidence.buildReferencesContext(g.articles)).append("\n");
        }

        final String rawSourcesBlock = extraContext.toString().trim().isEmpty() ? null : extraContext.toString().trim();
        final String sourceUrl = g.physio != null ? g.physio.sourceUrl
                : (!g.articles.isEmpty() ? g.articles.get(0).url()
                : (g.wiki != null ? g.wiki.sourceUrl : (g.pubmed != null ? g.pubmed.sourceUrl : null)));
        final String sourceLabel = buildSourceLabel(ctx, grounding != null ? grounding.caseCount : 0, g,
                cloudDocsContext != null, hasDocument, documentName, deep);
        final String clinicalModel = AiModelSelector.forClinical();
        final List<PubMedXml.Article> articles = g.articles;

        final StringConsumer finish = reply -> {
            String finalReply = deep && !articles.isEmpty()
                    ? DeepEvidence.finalizeCitations(reply, articles, ctx.getString(R.string.ai_refs_title))
                    : reply;
            callback.onGroundedReply(finalReply, sourceLabel, sourceUrl);
        };

        if (rawSourcesBlock == null) {
            runFormulationStage(ctx, text, query, null, historyContext, depth, false, clinicalModel, stages,
                    finish, callback::onError);
            return;
        }

        if (!deep) {
            runFormulationStage(ctx, text, query, "Attached sources (raw, use what is relevant):\n" + rawSourcesBlock,
                    historyContext, depth, false, clinicalModel, stages, finish, callback::onError);
            return;
        }

        if (stages != null) stages.onSearching();
        String aggregationPrompt = AiPrompts.buildAggregationPrompt(query, rawSourcesBlock);
        AiClient.sendMessage(aggregationPrompt, "Aggregate the relevant facts from each source above.", clinicalModel,
                new AiClient.Callback() {
            @Override
            public void onSuccess(String aggregated) {
                runAnalysisAndBeyond(ctx, text, query, aggregated, historyContext, patientId, clinicalModel,
                        stages, finish, callback::onError);
            }

            @Override
            public void onError(String message) {
                runFormulationStage(ctx, text, query, rawSourcesBlock, historyContext, AiIntent.DEPTH_DEEP, true,
                        clinicalModel, stages, finish, callback::onError);
            }
        });
    }

    private static String buildSourceLabel(Context ctx, int groundedCount, Gathered g, boolean hasCloud,
            boolean hasDocument, String documentName, boolean deep) {
        StringBuilder ext = new StringBuilder();
        if (g.physio != null) ext.append("Physiopedia (").append(g.physio.title).append(")");
        if (g.wiki != null) {
            if (ext.length() > 0) ext.append(" + ");
            ext.append(ctx.getString(R.string.ai_label_wikipedia));
        }
        if (g.pubmed != null) {
            if (ext.length() > 0) ext.append(" + ");
            ext.append("PubMed");
        }
        String cloud = hasCloud ? ctx.getString(R.string.ai_label_cloud_suffix) : "";
        String doc = hasDocument ? ctx.getString(R.string.ai_label_document_suffix) : "";

        if (deep) {
            String extra = ext.length() > 0 ? " + " + ext : "";
            return ctx.getString(R.string.ai_source_deep, g.articles.size(), extra + cloud + doc);
        }
        if (groundedCount > 0 && ext.length() > 0) {
            return ctx.getString(R.string.ai_source_protocols_external, groundedCount, ext.toString()) + cloud + doc;
        }
        if (groundedCount > 0) {
            return ctx.getString(R.string.ai_source_protocols_only, groundedCount) + cloud + doc;
        }
        if (ext.length() > 0) {
            return ctx.getString(R.string.ai_source_external_only, ext.toString()) + cloud + doc;
        }
        if (hasDocument) {
            String name = documentName != null && !documentName.trim().isEmpty() ? " (\"" + documentName.trim() + "\")" : "";
            return ctx.getString(R.string.ai_source_document) + name + cloud;
        }
        if (hasCloud) return ctx.getString(R.string.ai_source_cloud_only);
        return ctx.getString(R.string.ai_source_general);
    }

    private static void runAnalysisAndBeyond(Context ctx, String text, String query, String aggregated,
            String historyContext, String patientId, String clinicalModel,
            StageListener stages, StringConsumer onSuccess, ErrorConsumer onError) {
        String analysisPrompt = AiPrompts.buildAnalysisPrompt(query, aggregated, historyContext);
        AiClient.sendMessage(analysisPrompt, "Analyze the aggregation above in the required steps.", clinicalModel,
                new AiClient.Callback() {
            @Override
            public void onSuccess(String analysis) {
                if (stages != null) stages.onThinking();
                String patientCtx = (patientId != null && !patientId.trim().isEmpty())
                        ? PatientManager.buildRedactedPatientContext(ctx, patientId) : null;
                String matchingPrompt = AiPrompts.buildMatchingPrompt(query, analysis, historyContext, patientCtx);
                AiClient.sendMessage(matchingPrompt, "Verify the analysis above in the required steps.", clinicalModel,
                        new AiClient.Callback() {
                    @Override
                    public void onSuccess(String matched) {
                        runFormulationStage(ctx, text, query, buildFormulationContext(matched), historyContext,
                                AiIntent.DEPTH_DEEP, true, clinicalModel, stages, onSuccess, onError);
                    }

                    @Override
                    public void onError(String message) {
                        runFormulationStage(ctx, text, query, buildFormulationContext(analysis), historyContext,
                                AiIntent.DEPTH_DEEP, true, clinicalModel, stages, onSuccess, onError);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (stages != null) stages.onThinking();
                runFormulationStage(ctx, text, query, buildFormulationContext(aggregated), historyContext,
                        AiIntent.DEPTH_DEEP, true, clinicalModel, stages, onSuccess, onError);
            }
        });
    }

    private static String buildFormulationContext(String analyzedOrMatchedText) {
        if (analyzedOrMatchedText == null || analyzedOrMatchedText.trim().isEmpty()) return null;
        return "Verified analysis prepared by earlier research, aggregation and checking stages. Use it as the "
                + "basis of the answer, keep the reference numbers [n] exactly as they appear, and do not "
                + "re-derive it from scratch:\n" + analyzedOrMatchedText.trim();
    }

    private interface StringConsumer {
        void accept(String value);
    }

    private interface ErrorConsumer {
        void accept(String message);
    }

    private static void runFormulationStage(Context ctx, String text, String query, String matchedContext,
            String historyContext, int depth, boolean evidenceMode, String clinicalModel, StageListener stages,
            StringConsumer onSuccess, ErrorConsumer onError) {
        if (stages != null) stages.onThinking();
        String systemPromptToUse = AiPrompts.buildSystemPrompt(ctx, matchedContext, historyContext,
                text, query, depth, evidenceMode);
        sendWithAutoContinue(systemPromptToUse, text, text, "", clinicalModel, 0, new AiClient.Callback() {
            @Override
            public void onSuccess(String reply) {
                onSuccess.accept(reply);
            }

            @Override
            public void onError(String message) {
                onError.accept(message);
            }
        });
    }
}
