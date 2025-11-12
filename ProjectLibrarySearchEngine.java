// Project: Library Search Engine with Auto-Suggestions
// Java (Swing) — single-file demo with multiple classes defined below.
// Save as ProjectLibrarySearchEngine.java and run with: javac ProjectLibrarySearchEngine.java && java ProjectLibrarySearchEngine

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// ---------------------------
// Model: Book
// ---------------------------
class Book {
    private final String id;
    private final String title;
    private final String author;
    private final List<String> tags;

    public Book(String id, String title, String author, List<String> tags) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.tags = new ArrayList<>(tags);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }

    @Override
    public String toString() {
        return String.format("%s — %s (%s)", title, author, String.join(", ", tags));
    }
}

// ---------------------------
// Trie for autocomplete (stores strings with references)
// ---------------------------
class Trie {
    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        boolean isWord = false;
        List<String> values = new ArrayList<>();
    }

    private final Node root = new Node();

    public void insert(String key, String value) {
        Node cur = root;
        String lower = key.toLowerCase(Locale.ROOT);
        cur.values.add(value);
        for (char ch : lower.toCharArray()) {
            cur = cur.children.computeIfAbsent(ch, c -> new Node());
            cur.values.add(value);
        }
        cur.isWord = true;
    }

    public List<String> suggest(String prefix, int limit) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        Node cur = root;
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (char ch : lower.toCharArray()) {
            cur = cur.children.get(ch);
            if (cur == null) return Collections.emptyList();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>(cur.values);
        return seen.stream().limit(limit).collect(Collectors.toList());
    }
}

// ---------------------------
// LibraryIndex: keeps books and indexes for fast suggestions
// ---------------------------
class LibraryIndex {
    private final Map<String, Book> booksById = new HashMap<>();
    private final Trie titleTrie = new Trie();
    private final Trie authorTrie = new Trie();
    private final Trie tagTrie = new Trie();

    public void addBook(Book b) {
        booksById.put(b.getId(), b);
        titleTrie.insert(b.getTitle(), b.getId());
        authorTrie.insert(b.getAuthor(), b.getId());
        for (String tag : b.getTags()) tagTrie.insert(tag, b.getId());
    }

    public Optional<Book> getById(String id) {
        return Optional.ofNullable(booksById.get(id));
    }

    public List<String> suggestQueries(String prefix, int limit) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (String id : titleTrie.suggest(prefix, limit)) {
            Book b = booksById.get(id);
            if (b != null) suggestions.add(b.getTitle());
            if (suggestions.size() >= limit) return new ArrayList<>(suggestions);
        }
        for (String id : authorTrie.suggest(prefix, limit)) {
            Book b = booksById.get(id);
            if (b != null) suggestions.add(b.getAuthor());
            if (suggestions.size() >= limit) return new ArrayList<>(suggestions);
        }
        for (String id : tagTrie.suggest(prefix, limit)) {
            Book b = booksById.get(id);
            if (b != null) {
                for (String tag : b.getTags()) {
                    if (tag.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                        suggestions.add(tag);
                        break;
                    }
                }
            }
            if (suggestions.size() >= limit) return new ArrayList<>(suggestions);
        }
        return new ArrayList<>(suggestions).subList(0, Math.min(limit, suggestions.size()));
    }

    public List<Book> search(String query, int limit) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return Collections.emptyList();
        List<Book> results = new ArrayList<>();
        for (Book b : booksById.values()) {
            String title = b.getTitle().toLowerCase(Locale.ROOT);
            String author = b.getAuthor().toLowerCase(Locale.ROOT);
            boolean matched = false;
            if (title.contains(q) || author.contains(q)) matched = true;
            else for (String tag : b.getTags()) if (tag.toLowerCase(Locale.ROOT).contains(q)) matched = true;
            if (matched) results.add(b);
        }
        return results.stream().limit(limit).collect(Collectors.toList());
    }
}

// ---------------------------
// Search Engine: builds sample data and exposes index
// ---------------------------
class SearchEngine {
    private final LibraryIndex index = new LibraryIndex();

    public SearchEngine() {
        loadSampleData();
    }

    public LibraryIndex getIndex() { return index; }

    private void loadSampleData() {
        index.addBook(new Book("b1", "Introduction to Algorithms", "Thomas H. Cormen", Arrays.asList("algorithms", "cs", "textbook")));
        index.addBook(new Book("b2", "Clean Code", "Robert C. Martin", Arrays.asList("programming", "software", "best practices")));
        index.addBook(new Book("b3", "Design Patterns", "Erich Gamma", Arrays.asList("design", "patterns", "oop")));
        index.addBook(new Book("b4", "Effective Java", "Joshua Bloch", Arrays.asList("java", "programming")));
        index.addBook(new Book("b5", "The Pragmatic Programmer", "Andrew Hunt", Arrays.asList("programming", "software")));
    }
}

// ---------------------------
// Main UI: Swing-based quick demo
// ---------------------------
public class ProjectLibrarySearchEngine {
    private final SearchEngine engine = new SearchEngine();
    private final JFrame frame = new JFrame("Library Search — Autocomplete Demo");
    private final JTextField searchField = new JTextField(30);
    private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
    private final JList<String> suggestionList = new JList<>(suggestionModel);
    private final DefaultListModel<String> resultModel = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(resultModel);

    public ProjectLibrarySearchEngine() {
        setupUI();
        attachHandlers();
    }

    private void setupUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        top.add(new JLabel("Search: "), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);

        suggestionList.setVisibleRowCount(5);
        JScrollPane suggestionPane = new JScrollPane(suggestionList);
        suggestionPane.setPreferredSize(new Dimension(400, 100));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel middle = new JPanel(new BorderLayout(4, 4));
        middle.setBorder(BorderFactory.createTitledBorder("Suggestions"));
        middle.add(suggestionPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4,4));
        bottom.setBorder(BorderFactory.createTitledBorder("Results"));
        JScrollPane resultPane = new JScrollPane(resultList);
        resultPane.setPreferredSize(new Dimension(400, 200));
        bottom.add(resultPane, BorderLayout.CENTER);

        split.setTopComponent(middle);
        split.setBottomComponent(bottom);
        split.setResizeWeight(0.3);

        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void attachHandlers() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateSuggestions(); }
            public void removeUpdate(DocumentEvent e) { updateSuggestions(); }
            public void changedUpdate(DocumentEvent e) { updateSuggestions(); }
        });

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = suggestionList.getSelectedValue();
                    if (sel != null) {
                        searchField.setText(sel);
                        performSearch(sel);
                    }
                }
            }
        });

        searchField.addActionListener(e -> performSearch(searchField.getText()));

        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = resultList.getSelectedValue();
                    if (sel != null) {
                        JOptionPane.showMessageDialog(frame, sel, "Book Details", JOptionPane.PLAIN_MESSAGE);
                    }
                }
            }
        });
    }

    private void updateSuggestions() {
        SwingUtilities.invokeLater(() -> {
            String text = searchField.getText().trim();
            suggestionModel.clear();
            if (text.isEmpty()) return;
            List<String> suggestions = engine.getIndex().suggestQueries(text, 8);
            for (String s : suggestions) suggestionModel.addElement(s);
        });
    }

    private void performSearch(String query) {
        SwingUtilities.invokeLater(() -> {
            resultModel.clear();
            List<Book> results = engine.getIndex().search(query, 50);
            if (results.isEmpty()) resultModel.addElement("No results found for '" + query + "'");
            else for (Book b : results) resultModel.addElement(b.toString());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ProjectLibrarySearchEngine());
    }
}