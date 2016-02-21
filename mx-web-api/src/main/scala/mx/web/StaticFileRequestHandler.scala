package mx.web

import java.io.File
import java.net.URLDecoder
import java.nio.file.{Files, FileSystems, Path, Paths}
import java.text.SimpleDateFormat
import java.util.Locale

import javax.activation.MimetypesFileTypeMap

import org.slf4j.LoggerFactory

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.{HttpRequest, HttpResponse, HttpResponseStatus}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1

import HandlerUtils._

object StaticFileRequestHandler {
  private val logger = LoggerFactory.getLogger(classOf[StaticFileRequestHandler])

  private val Fs = FileSystems.getDefault
  private val MimeType = new MimetypesFileTypeMap

  private def getFileResponse(file: File, cacheSeconds: Int) = {
    val (fileSize, filePath) = (file.length, Paths.get(file.getPath))
    val response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(Files.readAllBytes(filePath)))
    response
      .applyHeaders(
          CONTENT_TYPE -> MimeType.getContentType(file),
          CONTENT_LENGTH -> response.content.readableBytes)
      .applyCacheHeaders(file.lastModified, cacheSeconds)
  }
  
  private def getFilenameFromUri(uri: String): Either[HttpResponseStatus, String] = {
    val decoded = URLDecoder.decode(uri, "UTF-8")
    val requestedFilename = if (decoded.contains("?")) decoded.take(decoded.indexOf('?')) else decoded
    Right(requestedFilename)
  }
  
  private def getPath(baseDir: String, filename: String) = {
    val path = Fs.getPath(baseDir + filename);
    if (path.normalize.startsWith(baseDir)) {
      Right(path)
    } else {
      Left(FORBIDDEN)
    }
  }
  
  private def openFile(path: Path) = {
    val file = path.toFile
    if (file.exists && file.isFile) {
      Right(file)
    } else {
      logger.warn("[{}] not found", file.getAbsolutePath)
      Left(NOT_FOUND)
    }
  }
  
  def isModifiedSince(file: File, sinceHeader: String) = if (sinceHeader != null) {
     val ifModifiedSinceDate = new SimpleDateFormat(HttpDateFormat, Locale.US).parse(sinceHeader)
     (ifModifiedSinceDate.getTime / 1000) != (file.lastModified / 1000)
  } else {
    true
  }
}

@ChannelHandler.Sharable
class StaticFileRequestHandler(documentRoot: String, cacheSeconds: Int) extends SimpleChannelInboundHandler[HttpRequest] {
  import StaticFileRequestHandler._
  
  override def channelRead0(ctx: ChannelHandlerContext, req: HttpRequest) = {
    val requestedFile = for {
      filename <- getFilenameFromUri(req.getUri).right
      path <- getPath(documentRoot, filename).right
      file <- openFile(path).right
    } yield file

    val response = requestedFile match {
      case Right(file) ⇒
        val sinceHeader = req.headers.get(IF_MODIFIED_SINCE)
        if (isModifiedSince(file, sinceHeader)) {
          getFileResponse(file, cacheSeconds) 
        } else {
          (NOT_MODIFIED: HttpResponse).applyCacheHeaders(file.lastModified, cacheSeconds)
        }
      case Left(status) ⇒
        (status: HttpResponse).applyCacheHeaders(System.currentTimeMillis, cacheSeconds)
    }
    
    logger.trace("Request for [{}] - HTTP {}", req.getUri, response.getStatus)
    
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    logger.error("Closing channel, exception caught:", cause)
    ctx.close()
  } 
}
