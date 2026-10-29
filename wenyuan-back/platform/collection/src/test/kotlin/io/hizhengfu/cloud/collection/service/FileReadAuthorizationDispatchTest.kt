package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.DefaultListableBeanFactory

class FileReadAuthorizationDispatchTest {
    @Test fun `selected archive authorizes shared bytes while every source still contributes its content baseline`() {
        val original=FileContentBaseline.read("same bytes".byteInputStream())
        var selectedAllowed=true
        var otherBaseline=original
        val archive=object:FileReadArchiveEvidenceGuard {
            override fun isProtected(path:String,storageType:String?)=true
            override fun requireReadable(path:String,storageType:String?)=throw BadRequestException("unscoped archive read")
            override fun checkReadable(path:String,storageType:String?):Boolean=throw BadRequestException("unscoped archive read")
            override fun readEvidence(path:String,storageType:String?):FileReadEvidence=throw BadRequestException("unscoped archive read")
            override fun readArchiveEvidence(path:String,storageType:String?,archiveId:String,fileId:String):FileReadEvidence {
                if (!selectedAllowed) throw BadRequestException("selected archive duty revoked")
                return FileReadEvidence(archiveId=="acceptance" && fileId=="file",listOf(original))
            }
        }
        val reused=object:FileReadGuard {
            override fun isProtected(path:String,storageType:String?)=true
            override fun requireReadable(path:String,storageType:String?)=throw BadRequestException("unrelated case duty absent")
            override fun contentBaselines(path:String,storageType:String?)=listOf(otherBaseline)
        }
        val beans=DefaultListableBeanFactory()
        beans.registerSingleton("archive",archive);beans.registerSingleton("reused",reused)
        val access=FileReadAccess(beans.getBeanProvider(FileReadGuard::class.java))
        assertThrows(BadRequestException::class.java) { access.readEvidence("shared.txt","LOCAL") }
        assertEquals(original,access.readArchiveEvidence("shared.txt","LOCAL","acceptance","file").baseline)
        assertThrows(BadRequestException::class.java) { access.readArchiveEvidence("shared.txt","LOCAL","other","file") }
        otherBaseline=FileContentBaseline.read("changed bytes".byteInputStream())
        assertThrows(BadRequestException::class.java) { access.readArchiveEvidence("shared.txt","LOCAL","acceptance","file") }
        otherBaseline=original;selectedAllowed=false
        assertThrows(BadRequestException::class.java) { access.readArchiveEvidence("shared.txt","LOCAL","acceptance","file") }
    }
    @Test fun `combined authorization never skips another guard or reuses a prior decision`() {
        val calls = mutableListOf<String>()
        var revoked = false
        val combined = object : FileReadAuthorizationGuard {
            override fun isProtected(path: String, storageType: String?): Boolean = error("Separate scan must not run")
            override fun requireReadable(path: String, storageType: String?) = error("Separate authorization must not run")
            override fun checkReadable(path: String, storageType: String?): Boolean {
                calls += "combined"
                return true
            }
        }
        val legacy = object : FileReadGuard {
            override fun isProtected(path: String, storageType: String?): Boolean { calls += "legacy-scan"; return true }
            override fun requireReadable(path: String, storageType: String?) {
                calls += "legacy-authorize"
                if (revoked) throw BadRequestException("synthetic revocation")
            }
        }
        val beans = DefaultListableBeanFactory()
        beans.registerSingleton("combined", combined); beans.registerSingleton("legacy", legacy)
        val access = FileReadAccess(beans.getBeanProvider(FileReadGuard::class.java))
        assertTrue(access.requireReadable("proof.txt", "LOCAL"))
        assertEquals(listOf("combined", "legacy-scan", "legacy-authorize"), calls)
        revoked = true; calls.clear()
        assertThrows(BadRequestException::class.java) { access.requireReadable("proof.txt", "LOCAL") }
        assertEquals(listOf("combined", "legacy-scan", "legacy-authorize"), calls)
    }
}
