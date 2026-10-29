package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.exception.HttpException
import io.hizhengfu.cloud.common.handle.GlobalExceptionHandler
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.FileReadGuard
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.web.FileViewController
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.catalina.startup.Tomcat
import org.springframework.context.support.StaticApplicationContext
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

/** 手动浏览器验收夹具；无 @Test，不进入默认测试，不连接业务数据库。 */
object CollectionImageBrowserProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Pass an existing temporary evidence directory" }
        val evidence = Path.of(args.single()).toRealPath()
        val root = Files.createTempDirectory(evidence, "fixture-")
        val data = Files.createDirectory(root.resolve("data"))
        val original = data.resolve("versioned.png")
        val protected = AtomicBoolean(false)
        val allowed = AtomicBoolean(true)
        val logLock = Any()
        fun log(kind: String, values: Map<String, Any?>) = synchronized(logLock) {
            Files.writeString(evidence.resolve("events.jsonl"), JsonUtils.toJson(
                mapOf("time" to Instant.now().toString(), "kind" to kind) + values
            ) + "\n", CREATE, APPEND)
        }
        fun replace(color: Color) {
            val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until 32) for (y in 0 until 32) image.setRGB(x, y, color.rgb)
            check(ImageIO.write(image, "png", original.toFile()))
            Files.setLastModifiedTime(original, FileTime.from(Instant.parse("2026-01-02T12:00:00Z")))
        }
        replace(Color.RED)
        // 图组验收的第二个独立来源；替换操作只改变 versioned.png。
        Files.copy(original, data.resolve("second.png"))
        StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("probeReadGuard", object : FileReadGuard {
                override fun isProtected(path: String, storageType: String?) = protected.get()
                override fun requireReadable(path: String, storageType: String?) {
                    if (!allowed.get()) throw BadRequestException("隔离夹具已撤销读取权限")
                }
            })
            context.refresh()
            val storage = LocalFileObjectStorage(data.toString())
            val access = FileReadAccess(context.getBeanProvider(FileReadGuard::class.java), storage)
            val controller = FileViewController(context, false, data.toString(), false, "unused.png", storage, access)
            val server = Tomcat()
            server.setBaseDir(root.resolve("tomcat").toString())
            server.setPort(0)
            val connector = server.connector
            connector.setProperty("address", "127.0.0.1")
            val web = server.addContext("", root.toString())
            Tomcat.addServlet(web, "probe", object : HttpServlet() {
                override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                    when (request.requestURI) {
                        "/userfiles/versioned.png", "/userfiles/second.png" -> {
                            try {
                                controller.downloadFile(request, response)
                            } catch (failure: HttpException) {
                                // 无 Spring DispatcherServlet；复用应用实际异常映射，不能将其误测为 Tomcat 500。
                                val result = GlobalExceptionHandler(false).httpException(failure)
                                response.status = result.statusCode.value()
                                result.headers.forEach { name, values -> values.forEach { response.addHeader(name, it) } }
                                response.contentType = "application/json;charset=UTF-8"
                                response.writer.write(JsonUtils.toJson(result.body))
                            }
                            log("image-response", mapOf(
                                "uri" to request.requestURI, "query" to request.queryString,
                                "ifNoneMatch" to request.getHeader("If-None-Match"),
                                "ifModifiedSince" to request.getHeader("If-Modified-Since"),
                                "status" to response.status, "etag" to response.getHeader("ETag"),
                                "cacheControl" to response.getHeader("Cache-Control"),
                                "lastModified" to response.getHeader("Last-Modified"),
                                "protected" to protected.get(), "allowed" to allowed.get()
                            ))
                        }
                        "/fixture" -> {
                            response.setHeader("Cache-Control", "no-store")
                            response.contentType = "text/html;charset=UTF-8"
                            response.writer.write(page)
                            log("document", mapOf("query" to request.queryString))
                        }
                        "/state" -> {
                            response.setHeader("Cache-Control", "no-store")
                            response.contentType = "application/json"
                            response.writer.write(JsonUtils.toJson(mapOf("protected" to protected.get(), "allowed" to allowed.get())))
                        }
                        else -> response.sendError(404)
                    }
                }

                override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
                    response.setHeader("Cache-Control", "no-store")
                    // 仅同源夹具页面可修改合成图片及内存权限状态。
                    if (request.getHeader("Origin") != "http://127.0.0.1:${connector.localPort}") {
                        response.sendError(403); return
                    }
                    when (request.requestURI) {
                        "/mutation" -> {
                            val action = request.getParameter("action")
                            when (action) {
                                "red" -> replace(Color.RED)
                                "blue" -> replace(Color.BLUE)
                                "protect" -> protected.set(true)
                                "deny" -> { protected.set(true); allowed.set(false) }
                                "reset" -> { protected.set(false); allowed.set(true); replace(Color.RED) }
                                else -> { response.sendError(400); return }
                            }
                            log("mutation", mapOf("action" to action))
                        }
                        "/observation" -> {
                            val observation = request.reader.readText().take(8192)
                            log("browser-observation", mapOf("payload" to observation))
                        }
                        else -> { response.sendError(404); return }
                    }
                    response.status = 204
                }
            })
            web.addServletMappingDecoded("/*", "probe")
            try {
                server.start()
                val url = "http://127.0.0.1:${connector.localPort}/fixture"
                Files.writeString(evidence.resolve("ready.json"), JsonUtils.toJson(mapOf("url" to url, "root" to root.toString())))
                log("started", mapOf("url" to url))
                val deadline = System.nanoTime() + java.time.Duration.ofMinutes(30).toNanos()
                while (!Files.exists(evidence.resolve("stop")) && System.nanoTime() < deadline) Thread.sleep(250)
            } finally {
                server.stop()
                server.destroy()
                log("stopped", emptyMap())
            }
        }
    }

    private val page = """<!doctype html>
<html lang="en"><meta charset="utf-8"><title>Collection image cache browser probe</title>
<style>body{font:16px system-ui;max-width:900px;margin:32px auto}button,a{margin:6px;padding:10px}img{width:180px;height:180px;image-rendering:pixelated;border:2px solid #555}pre{background:#eee;padding:16px;white-space:pre-wrap}</style>
<h1>Collection image cache browser probe</h1>
<p>Isolated synthetic data. Same image URL, fixed file modification time. Real file controller; simulated business permission.</p>
<div><button onclick="mutate('red')">Replace with red</button><button onclick="mutate('blue')">Replace with blue</button>
<button onclick="mutate('protect')">Protect (allowed)</button><button onclick="mutate('deny')">Revoke permission</button><button onclick="mutate('reset')">Reset fixture</button></div>
<div><button onclick="loadImage()">Recreate same URL image</button><button onclick="loadFreshImage()">Read image again from server</button><a id="navigate">Open new document</a></div>
<p id="state"></p><div id="image"></div><pre id="result">Waiting</pre>
<script>
const imageUrl='/userfiles/versioned.png?w=16';
let sequence=0;
let generation=0,objectUrl;
document.getElementById('navigate').href='/fixture?visit='+Date.now();
async function state(){const s=await (await fetch('/state')).json();document.getElementById('state').textContent=JSON.stringify(s);return s;}
async function record(value){
 const result={document:location.href,sequence:++sequence,...value};
 document.getElementById('result').textContent=JSON.stringify(result,null,2);
 await fetch('/observation',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(result)});
}
async function mutate(action){
 const response=await fetch('/mutation?action='+action,{method:'POST'});
 if(!response.ok)throw new Error('mutation '+response.status);
 await record({action,state:await state()});
}
function clearImage(){generation++;document.getElementById('image').replaceChildren();if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=undefined;return generation;}
function showImage(src,method,token){
 const img=new Image();img.alt='Synthetic test image';
 img.onload=async()=>{if(token!==generation)return;const canvas=document.createElement('canvas');canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;
 const c=canvas.getContext('2d');c.drawImage(img,0,0);await record({event:'load',method,url:imageUrl,width:img.naturalWidth,pixel:Array.from(c.getImageData(0,0,1,1).data),state:await state()});};
 img.onerror=async()=>{if(token===generation)await record({event:'error',method,url:imageUrl,state:await state()});};
 document.getElementById('image').replaceChildren(img);img.src=src;
}
function loadImage(){const token=clearImage();showImage(imageUrl,'img-src',token);}
async function loadFreshImage(){
 const token=clearImage();
 try{const response=await fetch(imageUrl,{cache:'no-store'});if(token!==generation)return;
 if(!response.ok)throw new Error('HTTP '+response.status);
 const blob=await response.blob();if(token!==generation)return;
 objectUrl=URL.createObjectURL(blob);showImage(objectUrl,'fetch-no-store',token);
 }catch(error){if(token===generation)await record({event:'error',method:'fetch-no-store',message:String(error),url:imageUrl,state:await state()});}
}
addEventListener('pagehide',clearImage);
state();loadImage();
</script></html>"""
}
