package io.hizhengfu.cloud.collection.service

import javax.imageio.*
import javax.imageio.metadata.IIOMetadata
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageWriterSpi
import javax.imageio.stream.ImageOutputStream

/** 保留实际 PNG 编码，仅在一次编码完成时执行测试中的业务变更。 */
internal fun <T> withPngEncodingHook(afterEncoding: () -> Unit, onceOnly: Boolean = true, block: () -> T): T {
    val registry=IIORegistry.getDefaultInstance()
    val original=ImageIO.getImageWritersByFormatName("png").next().let { writer -> writer.originatingProvider.also { writer.dispose() } }
    val once=java.util.concurrent.atomic.AtomicReference<(() -> Unit)?>(afterEncoding)
    val provider=object:ImageWriterSpi("test","1",arrayOf("png"),arrayOf("png"),arrayOf("image/png"),
        ImageWriter::class.java.name,arrayOf(ImageOutputStream::class.java),null,
        false,null,null,null,null,false,null,null,null,null) {
        override fun canEncodeImage(type:ImageTypeSpecifier)=original.canEncodeImage(type)
        override fun getDescription(locale:java.util.Locale?)="PNG encoding boundary fixture"
        override fun createWriterInstance(extension:Any?):ImageWriter {
            val delegate=original.createWriterInstance()
            return object:ImageWriter(this) {
                override fun setOutput(output:Any?) { super.setOutput(output);delegate.output=output }
                override fun getDefaultWriteParam()=delegate.defaultWriteParam
                override fun getDefaultStreamMetadata(param:ImageWriteParam?):IIOMetadata?=delegate.getDefaultStreamMetadata(param)
                override fun getDefaultImageMetadata(type:ImageTypeSpecifier,param:ImageWriteParam?):IIOMetadata?=delegate.getDefaultImageMetadata(type,param)
                override fun convertStreamMetadata(metadata:IIOMetadata,param:ImageWriteParam?):IIOMetadata?=delegate.convertStreamMetadata(metadata,param)
                override fun convertImageMetadata(metadata:IIOMetadata,type:ImageTypeSpecifier,param:ImageWriteParam?):IIOMetadata?=delegate.convertImageMetadata(metadata,type,param)
                override fun write(metadata:IIOMetadata?,image:IIOImage,param:ImageWriteParam?) {
                    delegate.write(metadata,image,param)
                    if (onceOnly) once.getAndSet(null)?.invoke() else afterEncoding()
                }
                override fun dispose() { delegate.dispose();super.dispose() }
            }
        }
    }
    val previous=registry.getServiceProviders(ImageWriterSpi::class.java,true).asSequence().filter { it.formatNames.any { name -> name.equals("png",true) } }.toList()
    registry.registerServiceProvider(provider)
    previous.forEach { registry.setOrdering(ImageWriterSpi::class.java,provider,it) }
    try { return block() } finally { registry.deregisterServiceProvider(provider) }
}
