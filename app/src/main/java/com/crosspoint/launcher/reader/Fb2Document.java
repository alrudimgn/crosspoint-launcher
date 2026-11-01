package com.crosspoint.launcher.reader;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.SAXParserFactory;

/**
 * Parses FictionBook 2.x (.fb2, .fb2.zip, .zip) documents into standard XHTML chapters
 * and resources, fully compatible with Crosspoint's low-memory reader architecture.
 */
public final class Fb2Document {

    public final File file;
    public final String title;
    public final String author;
    public final String language;
    public final List<String> chapters;
    public final List<Integer> spineIndices;
    public final List<EpubBook.TocEntry> toc;
    private final File bookCacheDir;
    private final File resDir;

    public static final class Metadata {
        public final String title;
        public final String author;
        public final String language;
        public final byte[] coverBytes;

        public Metadata(String title, String author, String language, byte[] coverBytes) {
            this.title = title;
            this.author = author;
            this.language = language;
            this.coverBytes = coverBytes;
        }
    }

    private Fb2Document(File file, String title, String author, String language,
                        List<String> chapters, List<Integer> spineIndices,
                        List<EpubBook.TocEntry> toc, File bookCacheDir) {
        this.file = file;
        this.title = title;
        this.author = author;
        this.language = language;
        this.chapters = Collections.unmodifiableList(chapters);
        this.spineIndices = Collections.unmodifiableList(spineIndices);
        this.toc = Collections.unmodifiableList(toc);
        this.bookCacheDir = bookCacheDir;
        this.resDir = new File(bookCacheDir, "res");
    }

    public static boolean isFb2(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".fb2") || name.endsWith(".fb2.zip")) return true;
        if (name.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String ename = entry.getName().toLowerCase(Locale.ROOT);
                    if (ename.endsWith(".fb2") && !ename.startsWith("__macosx")) {
                        return true;
                    }
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    public static String cleanHref(String href) {
        if (href == null) return "";
        String clean = href.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) clean = clean.substring(slash + 1);
        int bslash = clean.lastIndexOf('\\');
        if (bslash >= 0) clean = clean.substring(bslash + 1);
        return clean;
    }

    public String chapterHtml(int index) throws IOException {
        if (index < 0 || index >= chapters.size()) {
            throw new IOException("Chapter index out of range: " + index);
        }
        File chapterFile = new File(bookCacheDir, chapters.get(index));
        if (!chapterFile.exists()) {
            throw new IOException("Chapter file missing: " + chapterFile.getAbsolutePath());
        }
        byte[] bytes = Files_readAllBytes(chapterFile);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public byte[] resource(String href) throws IOException {
        String name = cleanHref(href);
        File resFile = new File(resDir, name);
        if (!resFile.exists()) {
            // Case-insensitive lookup
            File[] files = resDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().equalsIgnoreCase(name)) {
                        return Files_readAllBytes(f);
                    }
                }
            }
            throw new IOException("Resource not found: " + href);
        }
        return Files_readAllBytes(resFile);
    }

    private static byte[] Files_readAllBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length())) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        }
    }

    public static Fb2Document parse(File file, File cacheBaseDir) throws Exception {
        if (cacheBaseDir == null) {
            cacheBaseDir = new File(System.getProperty("java.io.tmpdir"), "crosspoint_fb2");
        }
        cacheBaseDir.mkdirs();

        String hash = sha1(file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length());
        File bookCacheDir = new File(cacheBaseDir, "fb2_" + hash);
        File metaFile = new File(bookCacheDir, "meta.json");

        if (metaFile.exists() && metaFile.length() > 0) {
            try {
                String jsonStr = new String(Files_readAllBytes(metaFile), StandardCharsets.UTF_8);
                long cachedMod = getJsonLong(jsonStr, "modified");
                long cachedSize = getJsonLong(jsonStr, "size");
                if (cachedMod == file.lastModified() && cachedSize == file.length()) {
                    String title = getJsonString(jsonStr, "title");
                    String author = getJsonString(jsonStr, "author");
                    String language = getJsonString(jsonStr, "language");
                    if (language.isEmpty()) language = "ru";

                    List<String> chapters = getJsonStringList(jsonStr, "chapters");
                    List<Integer> spineIndices = new ArrayList<>();
                    for (int i = 0; i < chapters.size(); i++) {
                        spineIndices.add(i);
                    }
                    List<EpubBook.TocEntry> toc = getJsonToc(jsonStr);
                    return new Fb2Document(file, title, author, language, chapters, spineIndices, toc, bookCacheDir);
                }
            } catch (Exception ignored) {
                // Cache corrupt or outdated; rebuild.
            }
        }

        bookCacheDir.mkdirs();
        File resDir = new File(bookCacheDir, "res");
        resDir.mkdirs();

        Fb2FullParser parser = new Fb2FullParser(file, bookCacheDir, resDir);
        parser.execute();

        // Write meta.json without external JSON library
        StringBuilder meta = new StringBuilder();
        meta.append("{\n");
        meta.append("  \"modified\": ").append(file.lastModified()).append(",\n");
        meta.append("  \"size\": ").append(file.length()).append(",\n");
        meta.append("  \"title\": \"").append(escapeJson(parser.title)).append("\",\n");
        meta.append("  \"author\": \"").append(escapeJson(parser.author)).append("\",\n");
        meta.append("  \"language\": \"").append(escapeJson(parser.language)).append("\",\n");
        meta.append("  \"chapters\": [");
        for (int i = 0; i < parser.chapters.size(); i++) {
            if (i > 0) meta.append(", ");
            meta.append("\"").append(escapeJson(parser.chapters.get(i))).append("\"");
        }
        meta.append("],\n");
        meta.append("  \"toc\": [\n");
        for (int i = 0; i < parser.toc.size(); i++) {
            EpubBook.TocEntry entry = parser.toc.get(i);
            if (i > 0) meta.append(",\n");
            meta.append("    {\"title\": \"").append(escapeJson(entry.title))
                .append("\", \"chapter\": ").append(entry.chapter)
                .append(", \"depth\": ").append(entry.depth)
                .append(", \"fragment\": \"").append(escapeJson(entry.fragment))
                .append("\"}");
        }
        meta.append("\n  ]\n}\n");

        File tmpMeta = new File(metaFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmpMeta)) {
            out.write(meta.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmpMeta.renameTo(metaFile)) {
            tmpMeta.delete();
        }

        return new Fb2Document(file, parser.title, parser.author, parser.language,
                parser.chapters, parser.spineIndices, parser.toc, bookCacheDir);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String getJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : "";
    }

    private static long getJsonLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1L;
    }

    private static List<String> getJsonStringList(String json, String key) {
        List<String> list = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (m.find()) {
            Pattern itemP = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
            Matcher itemM = itemP.matcher(m.group(1));
            while (itemM.find()) {
                list.add(unescapeJson(itemM.group(1)));
            }
        }
        return list;
    }

    private static List<EpubBook.TocEntry> getJsonToc(String json) {
        List<EpubBook.TocEntry> list = new ArrayList<>();
        Pattern p = Pattern.compile("\\{\\s*\"title\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*,\\s*\"chapter\"\\s*:\\s*(\\d+)\\s*,\\s*\"depth\"\\s*:\\s*(\\d+)(?:\\s*,\\s*\"fragment\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\")?\\s*\\}");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String title = unescapeJson(m.group(1));
            int chapter = Integer.parseInt(m.group(2));
            int depth = Integer.parseInt(m.group(3));
            String fragment = m.group(4) != null ? unescapeJson(m.group(4)) : "";
            list.add(new EpubBook.TocEntry(title, chapter, depth, fragment));
        }
        return list;
    }

    public static Metadata extractMetadata(File file) {
        try {
            return Fb2MetadataParser.extract(file);
        } catch (Exception e) {
            String title = file.getName();
            if (title.toLowerCase(Locale.ROOT).endsWith(".fb2.zip")) {
                title = title.substring(0, title.length() - 8);
            } else {
                int dot = title.lastIndexOf('.');
                if (dot > 0) title = title.substring(0, dot);
            }
            return new Metadata(title, "", "ru", null);
        }
    }

    public static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // --- Entity Filter Reader for non-standard HTML entities ---
    private static class EntityFilterReader extends FilterReader {
        private final StringBuilder buffer = new StringBuilder();
        private String replacement = null;
        private int replacementIndex = 0;

        EntityFilterReader(Reader in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            if (replacement != null) {
                if (replacementIndex < replacement.length()) {
                    return replacement.charAt(replacementIndex++);
                }
                replacement = null;
                replacementIndex = 0;
            }

            int c = super.read();
            if (c != '&') {
                return c;
            }

            buffer.setLength(0);
            buffer.append('&');
            while (buffer.length() < 12) {
                int next = super.read();
                if (next == -1) break;
                buffer.append((char) next);
                if (next == ';') break;
                if (!Character.isLetterOrDigit(next) && next != '#') {
                    break;
                }
            }

            String entity = buffer.toString();
            String resolved = resolveEntity(entity);
            if (resolved != null) {
                replacement = resolved;
                replacementIndex = 1;
                return replacement.charAt(0);
            } else {
                replacement = entity;
                replacementIndex = 1;
                return replacement.charAt(0);
            }
        }

        private static String resolveEntity(String entity) {
            switch (entity) {
                case "&nbsp;": return "&#160;";
                case "&laquo;": return "&#171;";
                case "&raquo;": return "&#187;";
                case "&mdash;": return "&#8212;";
                case "&ndash;": return "&#8211;";
                case "&hellip;": return "&#8230;";
                case "&copy;": return "&#169;";
                case "&reg;": return "&#174;";
                case "&trade;": return "&#8482;";
                case "&bull;": return "&#8226;";
                case "&lsquo;": return "&#8216;";
                case "&rsquo;": return "&#8217;";
                case "&ldquo;": return "&#8220;";
                case "&rdquo;": return "&#8221;";
                case "&sect;": return "&#167;";
                default: return null;
            }
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (len <= 0) return 0;
            int count = 0;
            while (count < len) {
                int c = read();
                if (c == -1) {
                    return count == 0 ? -1 : count;
                }
                cbuf[off + count] = (char) c;
                count++;
            }
            return count;
        }
    }

    private static String detectCharset(byte[] header, int length) {
        if (length >= 3 && (header[0] & 0xFF) == 0xEF && (header[1] & 0xFF) == 0xBB && (header[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        String ascii = new String(header, 0, Math.min(length, 1024), StandardCharsets.ISO_8859_1);
        Pattern pattern = Pattern.compile("(?i)<\\?xml[^>]+encoding=[\"']([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(ascii);
        if (matcher.find()) {
            String enc = matcher.group(1).trim();
            try {
                Charset.forName(enc);
                return enc;
            } catch (Exception ignored) { }
        }
        return "UTF-8";
    }

    private static InputStream openStream(File file, final ZipFile[] zipHolder) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip") || name.endsWith(".fb2.zip")) {
            ZipFile zip = new ZipFile(file);
            zipHolder[0] = zip;
            ZipEntry fb2Entry = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String ename = e.getName().toLowerCase(Locale.ROOT);
                if (ename.endsWith(".fb2") && !ename.startsWith("__macosx")) {
                    fb2Entry = e;
                    break;
                }
            }
            if (fb2Entry == null) {
                throw new IOException("No .fb2 entry inside zip: " + file.getName());
            }
            return zip.getInputStream(fb2Entry);
        } else {
            return new FileInputStream(file);
        }
    }

    // --- Fast Metadata Parser for scanning ---
    private static final class Fb2MetadataParser extends DefaultHandler {
        private static final class AbortParsingException extends SAXException {
            AbortParsingException() { super("Done parsing metadata"); }
        }

        private boolean inDescription, inTitleInfo, inBookTitle, inAuthor;
        private boolean inFirstName, inMiddleName, inLastName, inLang;
        private boolean inCoverpage;

        private final StringBuilder bookTitle = new StringBuilder();
        private final StringBuilder firstName = new StringBuilder();
        private final StringBuilder middleName = new StringBuilder();
        private final StringBuilder lastName = new StringBuilder();
        private final List<String> authors = new ArrayList<>();
        private final StringBuilder language = new StringBuilder();
        private String coverHref = null;

        static Metadata extract(File file) throws Exception {
            final ZipFile[] zipHolder = new ZipFile[1];
            try (InputStream raw = openStream(file, zipHolder)) {
                BufferedInputStream buffered = new BufferedInputStream(raw, 32768);
                buffered.mark(2048);
                byte[] header = new byte[2048];
                int read = buffered.read(header);
                buffered.reset();

                String charsetName = detectCharset(header, Math.max(0, read));
                FilterInputStream nonClosing = new FilterInputStream(buffered) {
                    @Override
                    public void close() {
                        // Prevent XMLReader from closing the underlying buffered stream when AbortParsingException occurs
                    }
                };
                Reader reader = new EntityFilterReader(new InputStreamReader(nonClosing, Charset.forName(charsetName)));

                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                XMLReader xmlReader = factory.newSAXParser().getXMLReader();

                Fb2MetadataParser handler = new Fb2MetadataParser();
                xmlReader.setContentHandler(handler);

                try {
                    xmlReader.parse(new InputSource(reader));
                } catch (AbortParsingException ignored) {
                }

                String title = handler.bookTitle.toString().trim();
                if (title.isEmpty()) {
                    title = file.getName();
                    if (title.toLowerCase(Locale.ROOT).endsWith(".fb2.zip")) {
                        title = title.substring(0, title.length() - 8);
                    } else {
                        int dot = title.lastIndexOf('.');
                        if (dot > 0) title = title.substring(0, dot);
                    }
                }
                String author = String.join(", ", handler.authors).trim();
                String lang = handler.language.toString().trim();
                if (lang.isEmpty()) lang = "ru";

                byte[] cover = null;
                if (handler.coverHref != null && !handler.coverHref.isEmpty()) {
                    cover = scanForCoverBinary(buffered, handler.coverHref);
                }
                return new Metadata(title, author, lang, cover);
            } finally {
                if (zipHolder[0] != null) {
                    try { zipHolder[0].close(); } catch (Exception ignored) { }
                }
            }
        }

        private static byte[] scanForCoverBinary(InputStream in, String coverHref) {
            String cleanTarget = cleanHref(coverHref).toLowerCase(Locale.ROOT);
            byte[] firstDecoded = null;
            try {
                byte[] tag = "<binary".getBytes(StandardCharsets.US_ASCII);
                int tagIdx = 0;
                int b;
                while ((b = in.read()) != -1) {
                    int c = (b >= 'A' && b <= 'Z') ? (b + 32) : b;
                    if (c == tag[tagIdx]) {
                        tagIdx++;
                        if (tagIdx == tag.length) {
                            tagIdx = 0;
                            // Found "<binary"
                            StringBuilder attr = new StringBuilder();
                            while ((b = in.read()) != -1) {
                                if (b == '>') break;
                                if (attr.length() < 256) attr.append((char) b);
                            }
                            String attrStr = attr.toString().toLowerCase(Locale.ROOT);
                            boolean matches = attrStr.contains(cleanTarget);

                            // Accumulate base64 content until "</binary"
                            ByteArrayOutputStream b64 = new ByteArrayOutputStream(65536);
                            byte[] closeTag = "</binary".getBytes(StandardCharsets.US_ASCII);
                            int closeIdx = 0;
                            while ((b = in.read()) != -1) {
                                int cc = (b >= 'A' && b <= 'Z') ? (b + 32) : b;
                                if (cc == closeTag[closeIdx]) {
                                    closeIdx++;
                                    if (closeIdx == closeTag.length) break;
                                } else {
                                    if (closeIdx > 0) {
                                        for (int i = 0; i < closeIdx; i++) b64.write(closeTag[i]);
                                        closeIdx = 0;
                                    }
                                    if (!Character.isWhitespace(b)) {
                                        b64.write(b);
                                    }
                                }
                            }
                            byte[] decoded = null;
                            try {
                                decoded = Base64.getMimeDecoder().decode(b64.toByteArray());
                            } catch (Exception ignored) { }

                            if (decoded != null && decoded.length > 0) {
                                if (matches) {
                                    return decoded;
                                }
                                if (firstDecoded == null) {
                                    firstDecoded = decoded;
                                }
                            }
                        }
                    } else {
                        tagIdx = (c == tag[0]) ? 1 : 0;
                    }
                }
            } catch (Exception ignored) { }
            return firstDecoded;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) throws SAXException {
            String tag = qName.toLowerCase(Locale.ROOT);
            if (tag.equals("description")) {
                inDescription = true;
            } else if (inDescription && tag.equals("title-info")) {
                inTitleInfo = true;
            } else if (inTitleInfo && tag.equals("book-title")) {
                inBookTitle = true;
            } else if (inTitleInfo && tag.equals("author")) {
                inAuthor = true;
                firstName.setLength(0);
                middleName.setLength(0);
                lastName.setLength(0);
            } else if (inAuthor && tag.equals("first-name")) {
                inFirstName = true;
            } else if (inAuthor && tag.equals("middle-name")) {
                inMiddleName = true;
            } else if (inAuthor && tag.equals("last-name")) {
                inLastName = true;
            } else if (inTitleInfo && tag.equals("lang")) {
                inLang = true;
            } else if (inTitleInfo && tag.equals("coverpage")) {
                inCoverpage = true;
            } else if (inCoverpage && tag.equals("image")) {
                String href = a.getValue("l:href");
                if (href == null) href = a.getValue("xlink:href");
                if (href == null) href = a.getValue("href");
                if (href != null) {
                    coverHref = cleanHref(href);
                }
            } else if (tag.equals("body")) {
                throw new AbortParsingException();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String tag = qName.toLowerCase(Locale.ROOT);
            if (tag.equals("description")) {
                inDescription = false;
                inTitleInfo = false;
                throw new AbortParsingException();
            } else if (tag.equals("title-info")) {
                inTitleInfo = false;
            } else if (tag.equals("book-title")) {
                inBookTitle = false;
            } else if (tag.equals("author") && inAuthor) {
                inAuthor = false;
                StringBuilder sb = new StringBuilder();
                if (firstName.length() > 0) sb.append(firstName);
                if (middleName.length() > 0) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(middleName);
                }
                if (lastName.length() > 0) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(lastName);
                }
                String name = sb.toString().trim();
                if (!name.isEmpty() && !authors.contains(name)) {
                    authors.add(name);
                }
            } else if (tag.equals("first-name")) {
                inFirstName = false;
            } else if (tag.equals("middle-name")) {
                inMiddleName = false;
            } else if (tag.equals("last-name")) {
                inLastName = false;
            } else if (tag.equals("lang")) {
                inLang = false;
            } else if (tag.equals("coverpage")) {
                inCoverpage = false;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inBookTitle) {
                bookTitle.append(ch, start, length);
            } else if (inFirstName) {
                firstName.append(ch, start, length);
            } else if (inMiddleName) {
                middleName.append(ch, start, length);
            } else if (inLastName) {
                lastName.append(ch, start, length);
            } else if (inLang && language.length() < 16) {
                language.append(ch, start, length);
            }
        }
    }

    // --- Full Parser for reading chapters and extracting resources ---
    private static final class Fb2FullParser extends DefaultHandler {
        private final File file;
        private final File bookCacheDir;
        private final File resDir;

        String title = "";
        String author = "";
        String language = "ru";
        final List<String> chapters = new ArrayList<>();
        final List<Integer> spineIndices = new ArrayList<>();
        final List<EpubBook.TocEntry> toc = new ArrayList<>();

        private boolean inDescription, inTitleInfo, inBookTitle, inAuthor;
        private boolean inFirstName, inMiddleName, inLastName, inLang;
        private boolean inCoverpage, inBody, isNotesBody;
        private int sectionDepth = 0;
        private boolean inTitle, inEpigraph, inSubtitle, inParagraph;
        private boolean inVerse, inStanza, inTextAuthor, inEmphasis, inStrong;
        private boolean inBinary;

        private final StringBuilder bookTitle = new StringBuilder();
        private final StringBuilder firstName = new StringBuilder();
        private final StringBuilder middleName = new StringBuilder();
        private final StringBuilder lastName = new StringBuilder();
        private final List<String> authors = new ArrayList<>();
        private final StringBuilder langBuffer = new StringBuilder();
        private String coverHref = null;

        private String currentBinaryId = null;
        private final StringBuilder binaryBuffer = new StringBuilder();

        private final StringBuilder currentChapterHtml = new StringBuilder();
        private final StringBuilder currentChapterTitle = new StringBuilder();
        private int chapterCount = 0;
        private int paragraphCount = 0;
        private int pendingTocDepth = 0;

        Fb2FullParser(File file, File bookCacheDir, File resDir) {
            this.file = file;
            this.bookCacheDir = bookCacheDir;
            this.resDir = resDir;
        }

        void execute() throws Exception {
            final ZipFile[] zipHolder = new ZipFile[1];
            try (InputStream raw = openStream(file, zipHolder)) {
                BufferedInputStream buffered = new BufferedInputStream(raw);
                buffered.mark(2048);
                byte[] header = new byte[2048];
                int read = buffered.read(header);
                buffered.reset();

                String charsetName = detectCharset(header, Math.max(0, read));
                FilterInputStream nonClosing = new FilterInputStream(buffered) {
                    @Override
                    public void close() {
                        // Prevent XMLReader from closing the underlying buffered stream when AbortParsingException occurs
                    }
                };
                Reader reader = new EntityFilterReader(new InputStreamReader(nonClosing, Charset.forName(charsetName)));

                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(false);
                XMLReader xmlReader = factory.newSAXParser().getXMLReader();
                xmlReader.setContentHandler(this);
                xmlReader.parse(new InputSource(reader));
            } finally {
                if (zipHolder[0] != null) {
                    try { zipHolder[0].close(); } catch (Exception ignored) { }
                }
            }

            // Finish any remaining chapter content
            if (currentChapterHtml.length() > 0 && hasContent(currentChapterHtml)) {
                finishChapter();
            }

            if (chapters.isEmpty()) {
                // Ensure at least one chapter exists
                currentChapterHtml.setLength(0);
                currentChapterHtml.append("<p>Пустая книга или не удалось разобрать текст.</p>");
                finishChapter();
            }

            title = bookTitle.toString().trim();
            if (title.isEmpty()) {
                title = file.getName();
                if (title.toLowerCase(Locale.ROOT).endsWith(".fb2.zip")) {
                    title = title.substring(0, title.length() - 8);
                } else {
                    int dot = title.lastIndexOf('.');
                    if (dot > 0) title = title.substring(0, dot);
                }
            }
            author = String.join(", ", authors).trim();
            language = langBuffer.toString().trim();
            if (language.isEmpty()) language = "ru";
        }

        private static boolean hasContent(CharSequence cs) {
            for (int i = 0; i < cs.length(); i++) {
                char c = cs.charAt(i);
                if (Character.isLetterOrDigit(c)) return true;
            }
            return false;
        }

        private void startNewChapter(String tocTitle, int depth) {
            if (currentChapterHtml.length() > 0 && hasContent(currentChapterHtml)) {
                finishChapter();
            }
            currentChapterHtml.setLength(0);
            currentChapterTitle.setLength(0);
            if (tocTitle != null) {
                currentChapterTitle.append(tocTitle.trim());
            }
            pendingTocDepth = depth;
            paragraphCount = 0;
        }

        private void finishChapter() {
            String chTitle = currentChapterTitle.toString().trim();
            if (chTitle.isEmpty()) {
                chTitle = "Глава " + (chapterCount + 1);
            }

            StringBuilder xhtml = new StringBuilder();
            xhtml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xhtml.append("<!DOCTYPE html>\n");
            xhtml.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n");
            xhtml.append("<meta charset=\"utf-8\"/>\n");
            xhtml.append("<title>").append(escapeXml(chTitle)).append("</title>\n");
            xhtml.append("</head>\n<body>\n");
            xhtml.append(currentChapterHtml);
            xhtml.append("\n</body>\n</html>\n");

            String fileName = "chapter_" + chapterCount + ".xhtml";
            File chapterFile = new File(bookCacheDir, fileName);
            try (FileOutputStream out = new FileOutputStream(chapterFile)) {
                out.write(xhtml.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                return;
            }

            chapters.add(fileName);
            spineIndices.add(chapterCount);
            toc.add(new EpubBook.TocEntry(chTitle, chapterCount, pendingTocDepth, ""));
            chapterCount++;
            currentChapterHtml.setLength(0);
            currentChapterTitle.setLength(0);
        }

        private static String escapeXml(String text) {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) {
            String tag = qName.toLowerCase(Locale.ROOT);
            if (tag.equals("description")) {
                inDescription = true;
            } else if (inDescription && tag.equals("title-info")) {
                inTitleInfo = true;
            } else if (inTitleInfo && tag.equals("book-title")) {
                inBookTitle = true;
            } else if (inTitleInfo && tag.equals("author")) {
                inAuthor = true;
                firstName.setLength(0);
                middleName.setLength(0);
                lastName.setLength(0);
            } else if (inAuthor && tag.equals("first-name")) {
                inFirstName = true;
            } else if (inAuthor && tag.equals("middle-name")) {
                inMiddleName = true;
            } else if (inAuthor && tag.equals("last-name")) {
                inLastName = true;
            } else if (inTitleInfo && tag.equals("lang")) {
                inLang = true;
            } else if (inTitleInfo && tag.equals("coverpage")) {
                inCoverpage = true;
            } else if (inCoverpage && tag.equals("image")) {
                String href = a.getValue("l:href");
                if (href == null) href = a.getValue("xlink:href");
                if (href == null) href = a.getValue("href");
                if (href != null) coverHref = cleanHref(href);
            } else if (tag.equals("body")) {
                inBody = true;
                String bodyName = a.getValue("name");
                isNotesBody = "notes".equalsIgnoreCase(bodyName) || "comments".equalsIgnoreCase(bodyName);
                if (isNotesBody) {
                    startNewChapter("Примечания", 0);
                    currentChapterHtml.append("<h2>Примечания</h2>\n");
                }
            } else if (inBody && tag.equals("section")) {
                sectionDepth++;
                if (sectionDepth == 1 || (sectionDepth == 2 && currentChapterHtml.length() > 1000)) {
                    startNewChapter(null, sectionDepth - 1);
                }
            } else if (inBody && tag.equals("title")) {
                inTitle = true;
                currentChapterHtml.append("<h2>");
            } else if (inBody && tag.equals("subtitle")) {
                inSubtitle = true;
                currentChapterHtml.append("<h3>");
            } else if (inBody && tag.equals("epigraph")) {
                inEpigraph = true;
                currentChapterHtml.append("<blockquote class=\"epigraph\">\n");
            } else if (inBody && tag.equals("cite")) {
                currentChapterHtml.append("<blockquote>\n");
            } else if (inBody && tag.equals("stanza")) {
                inStanza = true;
                currentChapterHtml.append("<div class=\"stanza\">\n");
            } else if (inBody && tag.equals("v")) {
                inVerse = true;
                currentChapterHtml.append("<p class=\"verse\">");
            } else if (inBody && tag.equals("text-author")) {
                inTextAuthor = true;
                currentChapterHtml.append("<p class=\"text-author\"><em>");
            } else if (inBody && tag.equals("p") && !inTitle) {
                inParagraph = true;
                paragraphCount++;
                // Split large sections if paragraph count exceeds threshold and sectionDepth == 0
                if (sectionDepth == 0 && paragraphCount >= 70 && !isNotesBody) {
                    startNewChapter(null, 0);
                }
                currentChapterHtml.append("<p>");
            } else if (inBody && tag.equals("empty-line")) {
                currentChapterHtml.append("<p class=\"empty-line\">&nbsp;</p>\n");
            } else if (inBody && tag.equals("emphasis")) {
                inEmphasis = true;
                currentChapterHtml.append("<em>");
            } else if (inBody && tag.equals("strong")) {
                inStrong = true;
                currentChapterHtml.append("<strong>");
            } else if (inBody && (tag.equals("strike") || tag.equals("strikethrough"))) {
                currentChapterHtml.append("<strike>");
            } else if (inBody && tag.equals("sub")) {
                currentChapterHtml.append("<sub>");
            } else if (inBody && tag.equals("sup")) {
                currentChapterHtml.append("<sup>");
            } else if (inBody && tag.equals("image")) {
                String href = a.getValue("l:href");
                if (href == null) href = a.getValue("xlink:href");
                if (href == null) href = a.getValue("href");
                if (href != null) {
                    String clean = cleanHref(href);
                    currentChapterHtml.append("<p class=\"image\"><img src=\"").append(escapeXml(clean)).append("\"/></p>\n");
                }
            } else if (inBody && tag.equals("a")) {
                String href = a.getValue("l:href");
                if (href == null) href = a.getValue("xlink:href");
                if (href == null) href = a.getValue("href");
                currentChapterHtml.append("<a");
                if (href != null) currentChapterHtml.append(" href=\"").append(escapeXml(href)).append("\"");
                currentChapterHtml.append(">");
            } else if (tag.equals("binary")) {
                inBinary = true;
                currentBinaryId = cleanHref(a.getValue("id"));
                binaryBuffer.setLength(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String tag = qName.toLowerCase(Locale.ROOT);
            if (tag.equals("description")) {
                inDescription = false;
                inTitleInfo = false;
            } else if (tag.equals("title-info")) {
                inTitleInfo = false;
            } else if (tag.equals("book-title")) {
                inBookTitle = false;
            } else if (tag.equals("author") && inAuthor) {
                inAuthor = false;
                StringBuilder sb = new StringBuilder();
                if (firstName.length() > 0) sb.append(firstName);
                if (middleName.length() > 0) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(middleName);
                }
                if (lastName.length() > 0) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(lastName);
                }
                String name = sb.toString().trim();
                if (!name.isEmpty() && !authors.contains(name)) {
                    authors.add(name);
                }
            } else if (tag.equals("first-name")) {
                inFirstName = false;
            } else if (tag.equals("middle-name")) {
                inMiddleName = false;
            } else if (tag.equals("last-name")) {
                inLastName = false;
            } else if (tag.equals("lang")) {
                inLang = false;
            } else if (tag.equals("coverpage")) {
                inCoverpage = false;
            } else if (tag.equals("body")) {
                inBody = false;
                isNotesBody = false;
            } else if (inBody && tag.equals("section")) {
                sectionDepth--;
            } else if (inBody && tag.equals("title")) {
                inTitle = false;
                currentChapterHtml.append("</h2>\n");
            } else if (inBody && tag.equals("subtitle")) {
                inSubtitle = false;
                currentChapterHtml.append("</h3>\n");
            } else if (inBody && tag.equals("epigraph")) {
                inEpigraph = false;
                currentChapterHtml.append("</blockquote>\n");
            } else if (inBody && tag.equals("cite")) {
                currentChapterHtml.append("</blockquote>\n");
            } else if (inBody && tag.equals("stanza")) {
                inStanza = false;
                currentChapterHtml.append("</div>\n");
            } else if (inBody && tag.equals("v")) {
                inVerse = false;
                currentChapterHtml.append("</p>\n");
            } else if (inBody && tag.equals("text-author")) {
                inTextAuthor = false;
                currentChapterHtml.append("</em></p>\n");
            } else if (inBody && tag.equals("p") && !inTitle) {
                inParagraph = false;
                currentChapterHtml.append("</p>\n");
            } else if (inBody && tag.equals("emphasis")) {
                inEmphasis = false;
                currentChapterHtml.append("</em>");
            } else if (inBody && tag.equals("strong")) {
                inStrong = false;
                currentChapterHtml.append("</strong>");
            } else if (inBody && (tag.equals("strike") || tag.equals("strikethrough"))) {
                currentChapterHtml.append("</strike>");
            } else if (inBody && tag.equals("sub")) {
                currentChapterHtml.append("</sub>");
            } else if (inBody && tag.equals("sup")) {
                currentChapterHtml.append("</sup>");
            } else if (inBody && tag.equals("a")) {
                currentChapterHtml.append("</a>");
            } else if (tag.equals("binary") && inBinary) {
                inBinary = false;
                if (currentBinaryId != null && !currentBinaryId.isEmpty()) {
                    try {
                        String b64 = binaryBuffer.toString().replaceAll("\\s+", "");
                        if (!b64.isEmpty()) {
                            byte[] decoded = Base64.getMimeDecoder().decode(b64);
                            File out = new File(resDir, currentBinaryId);
                            try (FileOutputStream fos = new FileOutputStream(out)) {
                                fos.write(decoded);
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inBookTitle) {
                bookTitle.append(ch, start, length);
            } else if (inFirstName) {
                firstName.append(ch, start, length);
            } else if (inMiddleName) {
                middleName.append(ch, start, length);
            } else if (inLastName) {
                lastName.append(ch, start, length);
            } else if (inLang && langBuffer.length() < 16) {
                langBuffer.append(ch, start, length);
            } else if (inBinary) {
                binaryBuffer.append(ch, start, length);
            } else if (inBody) {
                String text = new String(ch, start, length);
                if (inTitle) {
                    currentChapterTitle.append(text);
                }
                currentChapterHtml.append(escapeXml(text));
            }
        }
    }
}
