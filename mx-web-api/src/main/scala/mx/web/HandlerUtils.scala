package mx.web

import java.net.URLDecoder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, GregorianCalendar, Locale, TimeZone}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import io.netty.handler.codec.http.{DefaultFullHttpRequest, DefaultFullHttpResponse, HttpMethod, HttpRequest, HttpResponse, HttpResponseStatus}

object HandlerUtils {
  import scala.reflect.ClassTag
  import io.netty.handler.codec.http.HttpResponseStatus._
  import io.netty.handler.codec.http.HttpVersion._
  import io.netty.handler.codec.http.HttpHeaders._
  import io.netty.handler.codec.http.HttpHeaders.Names._

  type QueryMap = Map[String, Option[String]]
  
  val HttpDateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
    
  private val Json = "application/json"
  private val Mapper = new ObjectMapper()
  private val NameValue = """(.*?)(?:=(.*?))?""".r;
  
  Mapper.registerModule(DefaultScalaModule)
  
  implicit class RequestWrapper(val self: DefaultFullHttpRequest) extends AnyVal {
    def to[T](implicit tag: ClassTag[T]): T = {
      val content = self.content.toString(Charset.forName("UTF-8"))
      Mapper.readValue(content.getBytes, tag.runtimeClass).asInstanceOf[T]
    }
  }
  
  implicit class ResponseWrapper(val self: HttpResponse) extends AnyVal {
    def applyCorsHeaders = applyHeaders(
        "Access-Control-Allow-Origin" -> "*",
        "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE",
        "Access-Control-Allow-Headers" -> "Accept,Origin,Content-Type,X-MX-AuthToken",
        "Access-Control-Max-Age" -> 300)
    
    def applyHeaders(nameValues: (String, Any)*) = {
      nameValues.foreach { nameValue ⇒ self.headers.set(nameValue._1, nameValue._2) }
      self
    }
    
    def applyCacheHeaders(lastModified: Long, cacheSeconds: Int) = {
      val dateFormatter = new SimpleDateFormat(HttpDateFormat, Locale.US)
      dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"))
      val time = new GregorianCalendar()
      self.applyHeaders(DATE -> dateFormatter.format(time.getTime))
      time.add(Calendar.SECOND, cacheSeconds)
      self.applyHeaders(
          EXPIRES -> dateFormatter.format(time.getTime),
          CACHE_CONTROL -> s"max-age=${cacheSeconds}",
          LAST_MODIFIED -> dateFormatter.format(new Date(lastModified)))
    }
  }
  
  implicit class JsonResponse(val self: AnyRef) extends AnyVal {
    def toJsonResponse(): HttpResponse = toJsonResponse(OK)
    
    def toJsonResponse(status: HttpResponseStatus): HttpResponse = {
      val resp = new DefaultFullHttpResponse(HTTP_1_1, status)
      val out = new java.io.StringWriter
      Mapper.writeValue(out, self)
      val json = out.toString
      resp.content.writeBytes(json.getBytes("UTF-8"))
      resp.applyHeaders(
          CONTENT_TYPE -> Json,
          CONTENT_LENGTH -> resp.content.readableBytes)
    }
  }
  
  def toQueryMap(queryString: Option[String]): QueryMap = {
    if (!queryString.isEmpty) queryString.get.split('&')
      .foldLeft(Map.empty: QueryMap) { case (map, NameValue(n, v)) ⇒
        val name = URLDecoder.decode(n, "UTF-8")
        val value = if (v != null) URLDecoder.decode(v, "UTF-8") else v
        map + (name -> Option(value))
      }
    else Map.empty 
  }
  
  implicit def httpResponseStatusToHttpResponse(status: HttpResponseStatus): HttpResponse = new DefaultFullHttpResponse(HTTP_1_1, status)
}
