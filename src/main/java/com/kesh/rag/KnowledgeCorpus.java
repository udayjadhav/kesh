package com.kesh.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Indexes local Kesh/Vyasa/cache-gate markdown into the existing Lucene store.
 */
@Component
public class KnowledgeCorpus {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCorpus.class);
    private static final int MAX_HITS = 4;
    private static final long MAX_FILE_BYTES = 256_000;

    private final KeshProperties properties;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private FSDirectory directory;
    private IndexWriter writer;

    public KnowledgeCorpus(KeshProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void ingest() {
        if (!properties.getRag().isEnabled()) {
            return;
        }
        try {
            Path indexDir = Path.of(properties.getWorkspace().getDataDir()).resolve("knowledge-lucene");
            Files.createDirectories(indexDir);
            directory = FSDirectory.open(indexDir);
            writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
            writer.deleteAll();
            int files = 0;
            for (String raw : properties.getRag().getKnowledgePaths()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                files += indexPath(Path.of(raw.trim()).toAbsolutePath().normalize());
            }
            writer.commit();
            log.info("Knowledge corpus indexed {} files from {} roots", files, properties.getRag().getKnowledgePaths().size());
        } catch (IOException e) {
            log.warn("Knowledge corpus unavailable: {}", e.getMessage());
        }
    }

    public List<RelatedSnippet> retrieve(String queryText) {
        if (writer == null || directory == null || queryText == null || queryText.isBlank()) {
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
                TopDocs hits = searcher.search(parser.parse(escaped), MAX_HITS);
                List<RelatedSnippet> snippets = new ArrayList<>();
                for (ScoreDoc hit : hits.scoreDocs) {
                    Document document = searcher.storedFields().document(hit.doc);
                    String excerpt = document.get("text");
                    if (excerpt == null || excerpt.isBlank()) {
                        continue;
                    }
                    excerpt = excerpt.replaceAll("\\s+", " ").trim();
                    if (excerpt.length() > 700) {
                        excerpt = excerpt.substring(0, 700) + "…";
                    }
                    snippets.add(new RelatedSnippet(
                            document.get("path"),
                            document.get("title"),
                            excerpt,
                            hit.score
                    ));
                }
                return List.copyOf(snippets);
            }
        } catch (Exception e) {
            log.debug("Knowledge retrieve skip: {}", e.getMessage());
            return List.of();
        }
    }

    private int indexPath(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        if (Files.isRegularFile(root)) {
            return indexFile(root) ? 1 : 0;
        }
        int count = 0;
        try (Stream<Path> walk = Files.walk(root, 6)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                if (indexFile(path)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean indexFile(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".md") || name.endsWith(".mdc") || name.endsWith(".txt")
                || name.endsWith(".json") || name.endsWith(".yml"))) {
            return false;
        }
        if (name.contains("secret") || name.contains("credential") || name.contains("teams")
                || name.endsWith(".env") || name.contains("token")) {
            return false;
        }
        String text = Files.readString(path);
        if (text.isBlank()) {
            return false;
        }
        if (text.length() > 12_000) {
            text = text.substring(0, 12_000);
        }
        Document document = new Document();
        document.add(new StringField("path", path.toString(), Field.Store.YES));
        document.add(new StoredField("title", path.getFileName().toString()));
        document.add(new TextField("text", text, Field.Store.YES));
        writer.addDocument(document);
        return true;
    }

    @PreDestroy
    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // shutdown
            }
        }
        if (directory != null) {
            try {
                directory.close();
            } catch (IOException ignored) {
                // shutdown
            }
        }
    }
}
