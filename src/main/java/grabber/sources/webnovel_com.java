package grabber.sources;

import grabber.Chapter;
import grabber.GrabberUtils;
import grabber.Novel;
import grabber.NovelMetadata;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import system.data.accounts.Account;
import system.data.accounts.Accounts;
import system.init;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class webnovel_com implements Source {
    private final Novel novel;
    private Document toc;

    public webnovel_com(Novel novel) {
        this.novel = novel;
    }

    public List<Chapter> getChapterList() {
        List<Chapter> chapterList = new ArrayList();
        try {
            toc = Jsoup.connect(novel.novelLink).timeout(30 * 1000).get();
            String csrfToken = "null";
            String bookId = novel.novelLink;
            String bookTitle = toc.select("p.lh24.fs16.pt24.pb24.ell.c_000 span:not(span:contains(/))").first().text().replaceAll("[\\\\/:*?\"<>|]", "");
            bookId = toc.select("a#j_read").attr("data-report-bid");

            String otherParameter = "";
            CookieManager cookieManager = new CookieManager();
            CookieHandler.setDefault(cookieManager);

            URL url = new URL(novel.novelLink);
            URLConnection connection = url.openConnection();
            connection.getContent();

            List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
            for (HttpCookie cookie : cookies) {
                if (cookie.toString().startsWith("_csrfToken")) {
                    csrfToken = cookie.toString().substring(11);
                }
            }
            Map<String, String> webnovelChapters = null;
            String httpGetString = "https://www.webnovel.com/apiajax/chapter/GetChapterList?_csrfToken=" + csrfToken + "&bookId=" + bookId + "&_=" + otherParameter;

            //xhrRequest http = new xhrRequest();
            try {
                Document doc = Jsoup.connect(httpGetString).get();
                String jsonString = String.valueOf(doc.select("body").text());
                JSONObject jsonObject = (JSONObject) new JSONParser().parse(jsonString);
                JSONObject data = (JSONObject) jsonObject.get("data");
                JSONArray volumeItems = (JSONArray) data.get("volumeItems");
                webnovelChapters = new LinkedHashMap<>();

                for (Object o : volumeItems) {
                    JSONObject chapterItem = (JSONObject) o;
                    JSONArray chapterItems = (JSONArray) chapterItem.get("chapterItems");
                    for (Object a : chapterItems) {
                        JSONObject slide = (JSONObject) a;
                        String chapterId = String.valueOf(slide.get("id"));
                        // Crude hotfix
                        String chapterName = String.valueOf(slide.get("name")).replaceAll("â€™", "'");
                        String isVip = String.valueOf(slide.get("isVip"));
                        if (isVip.equals("0")) {
                            webnovelChapters.put(chapterId, chapterName);
                        }
                    }
                }
            } catch (ParseException | IOException e) {
                GrabberUtils.err(e.getMessage(), e);
            }

            int webnovelChapterNumber = 1;
            for (String chapterId : webnovelChapters.keySet()) {
                chapterList.add(new Chapter("Chapter " + webnovelChapterNumber + ": " + webnovelChapters.get(chapterId), "https://www.webnovel.com/book/" + bookId + "/" + chapterId + "/"
                        + bookTitle.replace(" ", "-") + "/" + webnovelChapters.get(chapterId).replace(" ", "-")));
                webnovelChapterNumber++;
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
            Document doc = Jsoup.connect(chapter.chapterURL).get();
            chapterBody = doc.select("div[class^=chapter_content]").first();
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
            metadata.setTitle(toc.select("p.lh24.fs16.pt24.pb24.ell.c_000 span:not(span:contains(/))").first().text());
            metadata.setDescription(toc.select(".j_synopsis").first().text());
            metadata.setBufferedCover(toc.select(".g_thumb img:eq(1)").attr("abs:src"));

            Elements tags = toc.select("a[href^=/category/list?category=].c_000");
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
        blacklistedTags.add("pirate");
        blacklistedTags.add(".cha-hr");
        blacklistedTags.add(".cha-info");
        blacklistedTags.add(".cha-tit p");
        blacklistedTags.add(".j_bottom_comment_area");
        blacklistedTags.add(".user-links-wrap");
        return blacklistedTags;
    }

    public Map<String, String> getLoginCookies() throws UnsupportedOperationException {
        GrabberUtils.info(novel.window, "Login...");
        if (init.gui != null) {
            init.gui.appendText(novel.window, "[INFO] Login...");
        }
        try {
            Account account = Accounts.getInstance().getAccount("WebNovel");
            if (!account.getUsername().isEmpty()) {
                Connection.Response res = Jsoup.connect("https://www.webnovel.com/")
                        .method(Connection.Method.GET)
                        .execute();
                res = Jsoup.connect("https://passport.webnovel.com/emaillogin.html?appid=900&areaid=1%2FloginSuccess&auto=1&autotime=0&ver=2") //I'm not sure if all these Variables in the URL have to be sent, this is just a straight copy from my Browser and it works.
                        .data("username", account.getUsername())
                        .data("password", account.getPassword())
                        .cookies(res.cookies())
                        .method(Connection.Method.POST)
                        .execute();
                return res.cookies();
            } else {
                GrabberUtils.err(novel.window, "No account found");
                return null;
            }

        } catch (IOException e) {
            GrabberUtils.err(novel.window, e.getMessage(), e);
        }
        throw new UnsupportedOperationException();
    }

}
