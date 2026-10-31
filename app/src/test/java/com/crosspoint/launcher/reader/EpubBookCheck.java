package com.crosspoint.launcher.reader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Run with scripts/check-reader.sh; no Android runtime or test dependency required. */
public final class EpubBookCheck {
    private static File epub(Map<String, String> entries) throws Exception {
        File file = Files.createTempFile("reader-check", ".epub").toFile();
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }
    private static Map<String, String> fixture() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/container.xml", "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>");
        entries.put("OPS/book.opf", "<package xmlns='http://www.idpf.org/2007/opf'><metadata><dc:title xmlns:dc='http://purl.org/dc/elements/1.1/'>A Book</dc:title></metadata><manifest>"
            + "<item id='b' href='text/second.xhtml' media-type='application/xhtml+xml'/>"
            + "<item id='a' href='text/first%20chapter.xhtml' media-type='application/xhtml+xml'/>"
            + "</manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>");
        entries.put("OPS/text/first chapter.xhtml", "<html><body><p>First &amp; best</p></body></html>");
        entries.put("OPS/text/second.xhtml", "<html><body><p>Second</p></body></html>");
        return entries;
    }
    private static void fails(Throwing action) throws Exception {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    interface Throwing { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        EpubBook book = new EpubBook(epub(fixture()));
        assert book.title.equals("A Book");
        assert book.language.isEmpty();
        Map<String, String> french = fixture();
        french.put("OPS/book.opf", french.get("OPS/book.opf").replace("</dc:title>", "</dc:title><dc:language xmlns:dc='http://purl.org/dc/elements/1.1/'> fr-CA </dc:language>"));
        assert new EpubBook(epub(french)).language.equals("fr-CA");
        assert book.chapters.size() == 2;
        assert book.chapterHtml(0).contains("First");
        assert book.chapterHtml(1).contains("Second");
        assert EpubBook.resolve("OPS/book.opf", "text/../first%20chapter.xhtml#p1").equals("OPS/first chapter.xhtml");
        fails(() -> EpubBook.resolve("OPS/book.opf", "../../escape"));
        fails(() -> EpubBook.resolve("OPS/book.opf", "%2e%2e/%2e%2e/escape"));
        fails(() -> EpubBook.resolve("OPS/book.opf", "https://example.com/book"));
        fails(() -> EpubBook.resolve("OPS/book.opf", "//example.com/book"));
        Map<String, String> missing = fixture(); missing.remove("OPS/text/second.xhtml");
        fails(() -> new EpubBook(epub(missing)));
        Map<String, String> huge = fixture(); huge.put("OPS/text/second.xhtml", "x".repeat(1024 * 1024 + 1));
        EpubBook large = new EpubBook(epub(huge));
        assert large.chapterHtml(0).contains("First"); // Only the chapter requested is decompressed.
        fails(() -> large.chapterHtml(1));
        Map<String, String> entities = fixture();
        entities.put("META-INF/container.xml", "<!DOCTYPE container [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><container>&x;</container>");
        fails(() -> new EpubBook(epub(entities)));
        Map<String, String> encrypted = fixture();
        encrypted.put("META-INF/encryption.xml", "<encryption><CipherReference URI='OPS/text/second.xhtml'/></encryption>");
        fails(() -> new EpubBook(epub(encrypted)));
        encrypted.put("META-INF/encryption.xml", "<encryption><CipherReference URI='OPS/font.ttf'/></encryption>");
        assert new EpubBook(epub(encrypted)).chapters.size() == 2; // Obfuscated fonts don't prevent native text reading.
        assert book.toc.isEmpty(); // No TOC: chapter selection falls back to file names.
        Map<String, String> nav = fixture();
        nav.put("OPS/book.opf", nav.get("OPS/book.opf").replace("</manifest>", "<item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/></manifest>"));
        nav.put("OPS/nav.xhtml", "<?xml version='1.0'?><!DOCTYPE html><html xmlns='http://www.w3.org/1999/xhtml' xmlns:epub='http://www.idpf.org/2007/ops'><body>"
            + "<nav epub:type='landmarks'><ol><li><a href='text/second.xhtml'>Landmark</a></li></ol></nav>"
            + "<nav epub:type='toc'><ol><li><a href='text/first%20chapter.xhtml'><span>Part   One</span></a><ol><li><a href='text/second.xhtml#s2'>Second</a></li></ol></li></ol></nav></body></html>");
        EpubBook navBook = new EpubBook(epub(nav));
        assert navBook.toc.size() == 2 && navBook.toc.get(0).title.equals("Part One") && navBook.toc.get(0).depth == 0;
        assert navBook.toc.get(1).chapter == 1 && navBook.toc.get(1).depth == 1 && navBook.toc.get(1).fragment.equals("s2");
        Map<String, String> ncx = fixture();
        ncx.put("OPS/book.opf", ncx.get("OPS/book.opf").replace("</manifest>", "<item id='ncx' href='toc.ncx' media-type='application/x-dtbncx+xml'/></manifest>").replace("<spine>", "<spine toc='ncx'>"));
        ncx.put("OPS/toc.ncx", "<ncx><navMap><navPoint><navLabel><text>One</text></navLabel><content src='text/first%20chapter.xhtml'/>"
            + "<navPoint><navLabel><text>Two</text></navLabel><content src='text/second.xhtml'/></navPoint></navPoint></navMap></ncx>");
        EpubBook ncxBook = new EpubBook(epub(ncx));
        assert ncxBook.toc.size() == 2 && ncxBook.toc.get(1).title.equals("Two") && ncxBook.toc.get(1).depth == 1;
        nav.put("OPS/nav.xhtml", "<html><body><nav><ol><li><a href='x'>&nbsp;broken</a></li></ol></nav></body></html>");
        assert new EpubBook(epub(nav)).toc.isEmpty(); // A broken TOC never blocks opening.
        System.out.println("EPUB reader checks passed");

        // --- FB2 tests ---
        String fb2Xml = "<?xml version='1.0' encoding='utf-8'?>\n"
            + "<FictionBook xmlns='http://www.gribuser.ru/xml/fictionbook/2.0' xmlns:l='http://www.w3.org/1999/xlink'>\n"
            + "  <description>\n"
            + "    <title-info>\n"
            + "      <genre>prose_classic</genre>\n"
            + "      <author>\n"
            + "        <first-name>Лев</first-name>\n"
            + "        <middle-name>Николаевич</middle-name>\n"
            + "        <last-name>Толстой</last-name>\n"
            + "      </author>\n"
            + "      <book-title>Кавказский пленник</book-title>\n"
            + "      <lang>ru</lang>\n"
            + "      <coverpage><image l:href='#cover.jpg'/></coverpage>\n"
            + "    </title-info>\n"
            + "  </description>\n"
            + "  <body>\n"
            + "    <section>\n"
            + "      <title><p>Кавказский пленник&nbsp;&mdash; Быль</p></title>\n"
            + "      <p>Служил на Кавказе офицером один барин.</p>\n"
            + "      <empty-line/>\n"
            + "      <p>Звали его <strong>Жилин</strong>. Пришло ему раз письмо из дома.</p>\n"
            + "    </section>\n"
            + "    <section>\n"
            + "      <title><p>Глава II</p></title>\n"
            + "      <p>Вот и поехали они вдвоем.</p>\n"
            + "    </section>\n"
            + "  </body>\n"
            + "  <binary id='cover.jpg' content-type='image/jpeg'>aGVsbG8=</binary>\n"
            + "</FictionBook>";

        File fb2File = Files.createTempFile("test-book", ".fb2").toFile();
        fb2File.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(fb2File)) {
            fos.write(fb2Xml.getBytes(StandardCharsets.UTF_8));
        }

        assert Fb2Document.isFb2(fb2File);
        Fb2Document.Metadata meta = Fb2Document.extractMetadata(fb2File);
        assert meta.title.equals("Кавказский пленник") : "Unexpected title: " + meta.title;
        assert meta.author.equals("Лев Николаевич Толстой") : "Unexpected author: " + meta.author;
        assert meta.coverBytes != null && new String(meta.coverBytes).equals("hello");

        EpubBook fb2Book = new EpubBook(fb2File);
        assert fb2Book.title.equals("Кавказский пленник");
        assert fb2Book.author.equals("Лев Николаевич Толстой");
        assert fb2Book.chapters.size() == 2;
        assert fb2Book.chapterHtml(0).contains("Жилин");
        assert fb2Book.chapterHtml(1).contains("Глава II");
        assert fb2Book.toc.size() == 2;
        assert fb2Book.toc.get(0).title.contains("Кавказский пленник");
        assert new String(fb2Book.resource(0, "cover.jpg")).equals("hello");

        // Test FB2.ZIP
        File fb2Zip = Files.createTempFile("test-book", ".fb2.zip").toFile();
        fb2Zip.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fb2Zip))) {
            zos.putNextEntry(new ZipEntry("book.fb2"));
            zos.write(fb2Xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assert Fb2Document.isFb2(fb2Zip);
        EpubBook zipBook = new EpubBook(fb2Zip);
        assert zipBook.title.equals("Кавказский пленник");
        assert zipBook.chapters.size() == 2;
        assert zipBook.chapterHtml(0).contains("Жилин");

        // Test Windows-1251 encoding
        String cp1251Xml = "<?xml version='1.0' encoding='windows-1251'?>\n"
            + "<FictionBook xmlns='http://www.gribuser.ru/xml/fictionbook/2.0'>\n"
            + "  <description>\n"
            + "    <title-info>\n"
            + "      <author><first-name>Иван</first-name><last-name>Тургенев</last-name></author>\n"
            + "      <book-title>Муму</book-title>\n"
            + "    </title-info>\n"
            + "  </description>\n"
            + "  <body>\n"
            + "    <section>\n"
            + "      <title><p>Муму</p></title>\n"
            + "      <p>В одной из отдаленных улиц Москвы...</p>\n"
            + "    </section>\n"
            + "  </body>\n"
            + "</FictionBook>";
        File cp1251File = Files.createTempFile("test-cp1251", ".fb2").toFile();
        cp1251File.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(cp1251File)) {
            fos.write(cp1251Xml.getBytes(java.nio.charset.Charset.forName("windows-1251")));
        }
        EpubBook cp1251Book = new EpubBook(cp1251File);
        assert cp1251Book.title.equals("Муму");
        assert cp1251Book.author.equals("Иван Тургенев");
        assert cp1251Book.chapterHtml(0).contains("Москвы");
        System.out.println("FB2 reader checks passed");
    }
}
