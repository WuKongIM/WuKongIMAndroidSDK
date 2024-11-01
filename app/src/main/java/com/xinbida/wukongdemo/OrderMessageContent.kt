package com.xinbida.wukongdemo

import com.xinbida.wukongim.msgmodel.WKMessageContent
import org.json.JSONObject

class OrderMessageContent : WKMessageContent() {
    var orderNo: String = ""
    var title: String = ""
    var imgUrl: String = ""
    var num: Int = 0
    var price: Int = 0
    override fun getDisplayContent(): String {
        return "[订单消息]"
    }

    init {
        type = 56
    }

    override fun encodeMsg(): JSONObject {
        val json = JSONObject()
        json.put("orderNo", orderNo)
        json.put("title", title)
        json.put("imgUrl", imgUrl)
        json.put("num", num)
        json.put("price", price)
        return json
    }

    override fun decodeMsg(jsonObject: JSONObject): WKMessageContent {
        orderNo = jsonObject.optString("orderNo")
        title = jsonObject.optString("title")
        imgUrl = jsonObject.optString("imgUrl")
        num = jsonObject.optInt("num")
        price = jsonObject.optInt("price")
        return this
    }
}