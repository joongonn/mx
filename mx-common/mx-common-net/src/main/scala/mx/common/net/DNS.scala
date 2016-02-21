package mx.common.net

import java.util.Hashtable

import javax.naming.Context
import javax.naming.directory.InitialDirContext

import org.slf4j.LoggerFactory

case class MxRecord(priority: Int, host: String)

// http://docs.oracle.com/javase/7/docs/technotes/guides/jndi/jndi-dns.html
// https://www.captechconsulting.com/blog/david-tiller/accessing-the-dusty-corners-dns-java
object DNS {
  private val logger = LoggerFactory.getLogger(DNS.getClass)
  
  private val RecordMatcher = """(\d+) (.*)\.""".r
  
  private val timeout = 1000
  private val retries = 3
  
  private val MxAttributeIds = Array[String]("MX")
  private val Env = {
    val props = new Hashtable[String, String]
    props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory")
    //props.put(Context.PROVIDER_URL, "dns://")
    //props.put("com.sun.jndi.dns.recursion", "true")
    props.put("com.sun.jndi.dns.timeout.initial", timeout.toString)
    props.put("com.sun.jndi.dns.timeout.retries", retries.toString)
    props.asInstanceOf[Hashtable[_, _]]
  }
  
  def getMxRecords(domain: String): List[MxRecord] = { 
    import scala.collection.JavaConverters._

    logger.debug("Resolving MxRecords for [{}]", domain)
    
    val ctx = new InitialDirContext(Env)
    val attributes = ctx.getAttributes(s"dns:/${domain}", MxAttributeIds)
    val mxAttributes = attributes.get("MX")

    val mxRecords = mxAttributes.getAll
        .asScala
        .toList
        .map { case RecordMatcher(priority, host) ⇒ MxRecord(priority.toInt, host) }

    mxRecords.sortBy(r ⇒ r.priority)
  }
}

