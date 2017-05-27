package xyz.yhsj.parse.extractors

import xyz.yhsj.parse.entity.MediaFile
import xyz.yhsj.parse.entity.MediaUrl
import xyz.yhsj.parse.entity.ParseResult
import xyz.yhsj.parse.intfc.Parse
import xyz.yhsj.parse.jsonObject
import xyz.yhsj.parse.match1
import xyz.yhsj.parse.utils.HttpRequest
import java.net.URL
import java.util.*

/**搜狐视频
 * Created by LOVE on 2017/4/19 019.
 *
 *
 */
object Sohu : Parse {
    override fun download(url: String): ParseResult {

        val tempUrl = url.replace("://m.", "://")


        try {
            val vid = getVid(url)
            println(vid)
            if (vid.isNullOrBlank()) {
                return ParseResult(code = 500, msg = "获取视频id失败")
            }
            return downloadByVid(tempUrl, vid)
        } catch (e: Exception) {
            return ParseResult(code = 500, msg = e.message ?: "未知错误")
        }
    }

    /**
     * 获取id
     */
    fun getVid(url: String): String {
        var vid: String? = ""
        if ("http://share.vrs.sohu.com" in url) {
            vid = "id=(\\d+)".match1(url)
        } else {
            val html = HttpRequest.get(url).body()

            vid = "\\Wvid\\s*[\\:=]\\s*['\"]?(\\d+)['\"]?".match1(html)
        }

        return vid ?: ""
    }

    /**
     * 新版视频地址获取,包含播放地址,下载地址,只处理播放地址m3u8,其他暂未处理
     * 还有一个关于专辑的接口,可以获取电视剧全部的剧集播放地址
     */
    fun downloadByVid2(vid: String): ParseResult {
        //专辑
        //http://api.tv.sohu.com/v4/album/videos/9353024.json?callback=jsonpx1495522722176_69_2&page=4&order=1&api_key=695fe827ffeb7d74260a813025970bd5&page_size=30&site=2&_=1495522722176
        //手机版播放信息
        //http://m.tv.sohu.com/phone_playinfo?vid=3745070&site=1&appid=tv&api_key=f351515304020cad28c92f70f002261c&plat=17&sver=1.0&partner=1&uid=1703021025088396&muid=1495522626363816&_c=1&pt=5&qd=680&src=11050001&_=1495523259647
        val info = HttpRequest.get("http://m.tv.sohu.com/phone_playinfo?vid=$vid").body().jsonObject

        val videoInfo = info.getJSONObject("data")

        val mediaFile = MediaFile()

        val video_name = videoInfo.getString("video_name")
        mediaFile.title = video_name


        val urls = videoInfo.getJSONObject("urls")

        val m3u8 = urls.getJSONObject("m3u8")

        val playKey = m3u8.keys()

        playKey.forEach {
            val mediaUrl = MediaUrl(video_name)
            mediaUrl.stream_type = it

            val tempUrls = m3u8.getJSONArray(it)
            mediaUrl.playUrl.add(tempUrls.getString(0))
            mediaUrl.downUrl.add(tempUrls.getString(0))
            mediaFile.url.add(mediaUrl)
        }

        return ParseResult(data = mediaFile)
    }


    /**
     * 获取连接
     */
    fun downloadByVid(url: String, vid: String): ParseResult {

        println(url)

        val mediaFile = MediaFile()

        if ("://tv.sohu.com/" in url) {

            return downloadByVid2(vid)

        } else {
            val info = HttpRequest.get("http://my.tv.sohu.com/play/videonew.do?vid=$vid&referer=http://my.tv.sohu.com").body().jsonObject
            println(">>>>>>>>>>>>>>>>>>>>>>>")
            println(info)

            val host = info.getString("allot")
            val prot = info.getString("prot")
            val tvid = info.getString("tvid")

            val data = info.getJSONObject("data")
            val title = data.getString("tvName")
            val size = data.getLong("totalBytes")
            mediaFile.title = title


            val sus = data.getJSONArray("su")
            val clipsURLs = data.getJSONArray("clipsURL")
            val cks = data.getJSONArray("ck")

            val mediaUrl = MediaUrl(title)

            for (i in 0..sus.length() - 1) {
                val su = sus.getString(i)
                val clip = clipsURLs.getString(i)
                val ck = cks.getString(i)

                var clipURL: String
                try {
                    clipURL = URL(clip).path
                } catch (e: Exception) {
                    clipURL = clip
                }

                val realUrl = real_url(host, vid, tvid, su, clipURL, ck)
                mediaUrl.playUrl.add(realUrl)
                mediaUrl.downUrl.add(realUrl)
            }
            mediaFile.url.add(mediaUrl)
        }


        println(mediaFile)

        return ParseResult(data = mediaFile)
    }

    /**
     * 获取真实地址
     */
    fun real_url(host: String, vid: String, tvid: String, new: String, clipURL: String, ck: String): String {
        val url = "http://$host/?prot=9&prod=flash&pt=1&file=$clipURL&new=$new&key=$ck&vid=$vid&uid=${Date().time}&t=${Math.random()}&rb=1"
        val realUrl = HttpRequest.get(url).body().jsonObject.getString("url")
        println(realUrl)
        return realUrl
    }
}