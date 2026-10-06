package com.kesh.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kesh.api.dto.TranscriptMessage;
import com.kesh.config.KeshProperties;
import com.kesh.persistence.ConversationArchive;
import com.kesh.workspace.WorkspaceMode;

import jakarta.annotation.PreDestroy;
import opennlp.tools.tokenize.SimpleTokenizer;

@Component
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);
    private static final int MAX_SNIPPETS = 3;
    private static final int MAX_CHARS = 1_500;

    private final boolean enabled;
    private final ConversationArchive archive;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final Map<WorkspaceMode, IndexWriter> writers = new EnumMap<>(WorkspaceMode.class);
    private final Map<WorkspaceMode, FSDirectory> directories = new EnumMap<>(WorkspaceMode.class);

    public ConversationMemory(KeshProperties properties, ConversationArchive archive) {
        this.enabled = properties.getRag().isEnabled();
        this.archive = archive;
        if (!enabled) {
            return;
        }
        for (WorkspaceMode mode : WorkspaceMode.values()) {
            try {
                java.nio.file.Path path = archive.luceneDir(mode);
                Files.createDirectories(path);
                FSDirectory directory = FSDirectory.open(path);
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
                directories.put(mode, directory);
                writers.put(mode, writer);
            } catch (IOException e) {
                log.warn("RAG index unavailable for {}: {}", mode.path(), e.getMessage());
            }
        }
    }

    public void index(WorkspaceMode mode, String conversationId, String title, List<TranscriptMessage> transcript) {
        if (!enabled) {
            return;
        }
        IndexWriter writer = writers.get(mode);
        if (writer == null || transcript == null || transcript.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        for (TranscriptMessage message : transcript) {
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(message.role()).append(": ").append(message.content());
        }
        if (body.isEmpty()) {
            return;
        }
        String text = body.length() > 8_000 ? body.substring(0, 8_000) : body.toString();
        String tokens = String.join(" ", SimpleTokenizer.INSTANCE.tokenize(text));
        try {
            writer.deleteDocuments(new Term("conversationId", conversationId));
            Document document = new Document();
            document.add(new StringField("conversationId", conversationId, Field.Store.YES));
            document.add(new StringField("mode", mode.path(), Field.Store.YES));
            document.add(new StoredField("title", title == null ? "" : title));
            document.add(new TextField("text", text, Field.Store.YES));
            document.add(new TextField("tokens", tokens, Field.Store.NO));
            writer.addDocument(document);
            writer.commit();
        } catch (IOException e) {
            log.debug("RAG index skip {}: {}", conversationId, e.getMessage());
        }
    }

    public List<RelatedSnippet> retrieve(WorkspaceMode mode, String excludeConversationId, String queryText) {
        if (!enabled || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        IndexWriter writer = writers.get(mode);
        FSDirectory directory = directories.get(mode);
        if (writer == null || directory == null) {
            return List.of();
        }
        try {
            writer.commit();
            if (!DirectoryReader.indexExists(directory)) {
                return List.of();
            }
            try (DirectoryReader reader = DirectoryReader.open(writer)) {
                if (reader.numDocs() == 0) {
                    return List.of();
                }
                IndexSearcher searcher = new IndexSearcher(reader);
                QueryParser parser = new QueryParser("text", analyzer);
                parser.setDefaultOperator(QueryParser.Operator.OR);
                String escaped = QueryParser.escape(queryText);
                if (escaped.isBlank()) {
                    return List.of();
                }
                Query textQuery = parser.parse(escaped);
                BooleanQuery.Builder builder = new BooleanQuery.Builder()
                        .add(textQuery, BooleanClause.Occur.MUST)
                        .add(new TermQuery(new Term("mode", mode.path())), BooleanClause.Occur.FILTER);
                if (excludeConversationId != null && !excludeConversationId.isBlank()) {
                    builder.add(new TermQuery(new Term("conversationId", excludeConversationId)), BooleanClause.Occur.MUST_NOT);
                }
                TopDocs hits = searcher.search(builder.build(), MAX_SNIPPETS);
                List<RelatedSnippet> snippets = new ArrayList<>();
                int remaining = MAX_CHARS;
                for (ScoreDoc hit : hits.scoreDocs) {
                    Document document = searcher.storedFields().document(hit.doc);
                    String excerpt = excerpt(document.get("text"), remaining);
                    if (excerpt.isBlank()) {
                        continue;
                    }
                    remaining -= excerpt.length();
                    snippets.add(new RelatedSnippet(
                            document.get("conversationId"),
                            document.get("title"),
                            excerpt,
                            hit.score
                    ));
                    if (remaining <= 0) {
                        break;
                    }
                }
                return List.copyOf(snippets);
            }
        } catch (Exception e) {
            log.debug("RAG retrieve skip: {}", e.getMessage());
            return List.of();
        }
    }

    public String promptBlock(List<RelatedSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Related notes from your recent chats in this desk:\n");
        for (RelatedSnippet snippet : snippets) {
            sb.append("- ").append(snippet.title() == null ? "chat" : snippet.title())
                    .append(": ").append(snippet.excerpt()).append('\n');
        }
        return sb.toString();
    }

    @PreDestroy
    public void close() {
        for (IndexWriter writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // shutdown
            }
        }
        for (FSDirectory directory : directories.values()) {
            try {
                directory.close();
            } catch (IOException ignored) {
                // shutdown
            }
        }
    }

    private static String excerpt(String text, int remaining) {
        if (text == null || remaining <= 0) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        int cap = Math.min(remaining, 500);
        if (compact.length() <= cap) {
            return compact;
        }
        return compact.substring(0, cap) + "…";
    }
}
