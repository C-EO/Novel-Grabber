package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class creativenovels_com implements Source {
    private final String name = "Creative Novels";
    private final String url = "https://creativenovels.com";
    private final boolean canHeadless = false;
    private Novel novel;
    private Document toc;

    public creativenovels_com(Novel novel) {
        this.novel = novel;
    }

    public creativenovels_com() {
    }

    public String getName() {
        return name;
    }

    public boolean canHeadless() {
        return canHeadless;
    }

    public String toString() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public List<Chapter> getChapterList() {
        List<Chapter> chapterList = new ArrayList();

        try {
            toc = Jsoup.connect(novel.novelLink).cookies(novel.cookies).get();
            Connection.Response res = Jsoup.connect("https://creativenovels.com/wp-admin/admin-ajax.php")
                    .method(Connection.Method.POST)
                    .cookies(novel.cookies)
                    .timeout(30 * 1000)
                    .data("action", "crn_chapter_list")
                    .data("view_id", toc.select("#chapter_list_novel_page").attr("class"))
                    .execute();
            Document doc = res.parse();
            String ajaxResp = doc.select("body").toString();
            ajaxResp = ajaxResp.replaceAll("success.define.", "");
            ajaxResp = ajaxResp.replaceAll(".data.available.end_data.", "");
            String[] test = ajaxResp.split(".data.");
            List<String> names = new ArrayList<>();
            List<String> links = new ArrayList<>();
            for (String line : test) {
                if (line.contains("locked.end")) break;
                if (line.contains("http")) {
                    links.add(line.substring(line.indexOf("http")));
                } else {
                    names.add(line);
                }
            }
            names.remove(names.size() - 1);
            for (int i = 0; i < links.size(); i++) {
                chapterList.add(new Chapter(names.get(i), links.get(i)));
            }
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }

        return chapterList;
    }

    public Element getChapterContent(Chapter chapter) {
        Element chapterBody = null;
        try {
            Document doc = Jsoup.connect(chapter.chapterURL).cookies(novel.cookies).get();
            chapterBody = doc.select(".entry-content.content").first();
        } catch (HttpStatusException httpEr) {
            GrabberUtils.err(novel.window, GrabberUtils.getHTMLErrMsg(httpEr));
        } catch (IOException e) {
            GrabberUtils.err(novel.window, "Could not connect to webpage!", e);
        }
        return chapterBody;
    }

    public NovelMetadata getMetadata() {
        NovelMetadata metadata = new NovelMetadata();

        if (toc != null) {
            metadata.setTitle(toc.select(".e45344-14").first().text());
            metadata.setAuthor(toc.select(".e45344-16 > a:nth-child(1)").first().text());
            metadata.setDescription(toc.select(".novel_page_synopsis").first().text());
            metadata.setBufferedCover(toc.select("img.book_cover").attr("abs:src"));

            Elements tags = toc.select("div.genre_novel");
            List<String> subjects = new ArrayList<>();
            for (Element tag : tags) {
                subjects.add(tag.text());
            }
            metadata.setSubjects(subjects);
        }

        return metadata;
    }

    public List<String> getBlacklistedTags() {
        List blacklistedTags = new ArrayList();
        blacklistedTags.add(".mNS");
        blacklistedTags.add(".support-placement");
        return blacklistedTags;
    }

}
