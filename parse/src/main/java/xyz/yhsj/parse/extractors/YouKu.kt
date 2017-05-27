package xyz.yhsj.parse.extractors

import xyz.yhsj.parse.entity.MediaFile
import xyz.yhsj.parse.entity.MediaUrl
import xyz.yhsj.parse.entity.ParseResult
import xyz.yhsj.parse.intfc.Parse
import xyz.yhsj.parse.jsonArray
import xyz.yhsj.parse.jsonObject
import xyz.yhsj.parse.match1
import xyz.yhsj.parse.utils.Base64
import xyz.yhsj.parse.utils.HttpRequest
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.xor


/**优酷解析
 * Created by LOVE on 2017/4/13 013.
 *
 * 2017/5/24地址失效,改为如下地址播放(后期处理)
 * https://ups.youku.com/ups/get.json?vid=XODMxNzI4MjQ4&ccode=0501&client_ip=0.0.0.0&client_ts=1495612499&utid=
 *
 */
object YouKu : Parse {
    val template1 = "becaf9be"
    val template2 = "bf7e5f01"

    override fun download(url: String): ParseResult {
        try {

            val vid = get_vid_from_url(url)
            if (vid.isNullOrBlank()) {
                return ParseResult(code = 500, msg = "获取视频id失败")
            }

            return getdata(vid)
        } catch (e: Exception) {
            return ParseResult(code = 500, msg = e.message ?: "未知错误")
        }

    }

    /**
     * 1、 根据url获取视频id
     *
     * @param url 视频网页地址
     * @return
     */
    fun get_vid_from_url(url: String): String {
        val regex1 = "youku\\.com/v_show/id_([a-zA-Z0-9=]+)"
        val regex2 = "player\\.youku\\.com/player\\.php/sid/([a-zA-Z0-9=]+)/v\\.swf"
        val regex3 = "loader\\.swf\\?VideoIDS=([a-zA-Z0-9=]+)"
        val regex4 = "player\\.youku\\.com/embed/([a-zA-Z0-9=]+)"
        val regex5 = "id_([a-zA-Z0-9=]+)"
        return regex1.match1(url) ?: regex2.match1(url) ?: regex3.match1(url) ?: regex4.match1(url) ?: regex5.match1(url) ?: ""
    }

    /**
     * 获取下载地址
     */
    fun getdata(vid: String): ParseResult {
        val api_url = "https://ups.youku.com/ups/get.json?vid=$vid&ccode=0501&client_ip=192.168.1.1&utid=&client_ts=${Date().time / 1000}"
        println(api_url)
        val videoInfo = HttpRequest
                .get(api_url)
                .header("Referer", "http://static.youku.com/")
                .header("Cookie", "__ysuid=${Date().time / 1000}")
                .body()
                .jsonObject

        val video = videoInfo.getJSONObject("data").getJSONObject("video")
        val title = video.getString("title")

        val mediaFile = MediaFile()
        mediaFile.title = title

        val streams = videoInfo.getJSONObject("data").getJSONArray("stream")
        for (i in 0..streams.length() - 1) {

            val mediaUrl = MediaUrl(title)

            val stream_type = getStreamType(streams.getJSONObject(i).getString("stream_type"))

            mediaUrl.stream_type = stream_type["msg"]

            val videoSource = streams.getJSONObject(i).getString("m3u8_url")

            mediaUrl.playUrl.add(videoSource)
            mediaUrl.downUrl.add(videoSource)

            mediaFile.url.add(mediaUrl)
        }
        return ParseResult(data = mediaFile)
    }


    /**
     * 获取下载地址
     * 接口失效啦
     */
    fun getdata2(vid: String): ParseResult {
        val url10 = "http://play.youku.com/play/get.json?ct=10&vid=$vid"
        val url12 = "http://play.youku.com/play/get.json?ct=12&vid=$vid"

        val resp12 = HttpRequest.get(url12).header("Referer", "http://static.youku.com/").header("Cookie", "__ysuid={}").body().jsonObject

        if (!resp12.isNull("error")) {
            return ParseResult(code = 500, msg = resp12.getJSONObject("error").getString("note"))
        }

        val resp10 = HttpRequest.get(url10).header("Referer", "http://static.youku.com/").header("Cookie", "__ysuid={}").body().jsonObject

        val security = resp12.getJSONObject("data").getJSONObject("security")
        val strEncrypt = security.getString("encrypt_string")

        val sidAndToken = getSidAndToken(strEncrypt)

        val mSid = sidAndToken[0]
        val mToken = sidAndToken[1]
        val mOip = security.getString("ip")

        val mediaFile = MediaFile()

        val video = resp10.getJSONObject("data").getJSONObject("video")
        val title = video.getString("title")
        mediaFile.title = title

        val streams = resp10.getJSONObject("data").getJSONArray("stream")

        for (i in 0..streams.length() - 1) {

            val mediaUrl = MediaUrl(title)

            val stream_type = getStreamType(streams.getJSONObject(i).getString("stream_type"))

            mediaUrl.stream_type = stream_type["msg"]

            var videoSource = "http://pl.youku.com/playlist/m3u8?vid=$vid&type=${stream_type["streamType"]}&ts=${Date().time / 1000}&keyframe=0&sid=$mSid&token=$mToken&ctype=12&ev=1&oip=$mOip&client_id=youkumobileplaypage"

            mediaUrl.playUrl.add(videoSource)
            mediaUrl.downUrl.add(videoSource)
            //下面用于下载视频
//            val segs = streams.getJSONObject(i).getJSONArray("segs")
//
//            for (j in 0..segs.length() - 1) {
//
//                val fileId = segs.getJSONObject(j).getString("fileid")
//                val ep = getEp(mSid, fileId, mToken)
//                val key = segs.getJSONObject(j).getString("key")
//
//                //下载地址,还得解析一次
//                val videoUrl = "http://k.youku.com/player/getFlvPath/sid/${mSid}_00/st/${stream_type["type"]}/fileid/$fileId?ctype=12&ep=$ep&ev=1&oip=$mOip&token=$mToken&yxon=1&K=$key"
//
//                val realUrl = HttpRequest.get(videoUrl).body().jsonArray
//
//                mediaUrl.downUrl.add(realUrl.getJSONObject(0).getString("server"))
//
//            }

            mediaFile.url.add(mediaUrl)
        }

        return ParseResult(data = mediaFile)
    }


    /**
     * 获取视频的格式与描述

     * @return
     */
    fun getStreamType(streamType: String): Map<String, String> {
        return when (streamType) {
            "mp4hd3", "hd3" -> mapOf("type" to "flv", "msg" to "1080", "streamType" to "hd3")
            "mp4hd2", "hd2" -> mapOf("type" to "flv", "msg" to "超清", "streamType" to "hd2")
            "mp4hd", "mp4" -> mapOf("type" to "mp4", "msg" to "高清", "streamType" to "mp4")
            "flvhd", "flv" -> mapOf("type" to "flv", "msg" to "标清", "streamType" to "flv")
            "3gphd" -> mapOf("type" to "3gp", "msg" to "标清(3GP)", "streamType" to "3gp")
            else -> mapOf("type" to "flv", "msg" to "标清", "streamType" to "flv")
        }
    }

    /**
     * 获取必要字段
     */
    fun getSidAndToken(encryptStr: String): List<String> {
        val s = String(youkuEncoder(template1.toByteArray(), Base64.decode(encryptStr)))
        val rst = s.split("_")
        return rst
    }

    /**
     * 获取必要字段
     */
    fun getEp(sid: String, fileId: String, token: String): String {
        val epT = youkuEncoder(template2.toByteArray(), "${sid}_${fileId}_${token}".toByteArray())
        return Base64.encode(epT)
    }

    fun youkuEncoder(b1: ByteArray, b2: ByteArray): ByteArray {
        val result = ByteArray(b2.size)

        val s = IntArray(256)
        for (i in 0..255) {
            s[i] = i
        }
        var t = 0
        var tmp: Int
        for (i in 0..255) {
            t = (t + s[i] + (b1[i % b1.size] and 0xff.toByte())) % 256
            tmp = s[i]
            s[i] = s[t]
            s[t] = tmp
        }
        var x = 0
        var y = 0
        for (i in 0..b2.size - 1) {
            y = (y + 1) % 256
            x = (x + s[y]) % 256
            tmp = s[x]
            s[x] = s[y]
            s[y] = tmp
            result[i] = (b2[i] and 0xff.toByte() xor s[(s[x] + s[y]) % 256].toByte())
        }
        return result

    }
}