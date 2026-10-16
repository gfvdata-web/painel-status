package io.github.gfvdataweb.painelstatus.data.ia

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda um único texto secreto (aqui, o cookie do painel de IA). Nos testes, um dublê em memória. */
interface CofreDeSegredo {
    /** O texto guardado, ou null (nada guardado ou não dá mais para decifrar). */
    fun ler(): String?

    fun guardar(valor: String)

    fun apagar()
}

class CofreEmMemoria(private var valor: String? = null) : CofreDeSegredo {
    override fun ler(): String? = valor

    override fun guardar(valor: String) {
        this.valor = valor
    }

    override fun apagar() {
        valor = null
    }
}

/**
 * Cifra o texto com AES-GCM; a chave fica no Android Keystore (não sai do
 * aparelho) e o texto cifrado (IV + conteúdo) num [arquivo] do app. Sem
 * biblioteca extra: EncryptedSharedPreferences está depreciada. O arquivo não
 * vai para backup (ver backup_rules.xml): em outro aparelho a chave não existe.
 */
class CofreKeystore(private val arquivo: File) : CofreDeSegredo {

    override fun ler(): String? {
        if (!arquivo.isFile) return null
        return try {
            val bytes = arquivo.readBytes()
            val cifra = Cipher.getInstance(TRANSFORMACAO)
            cifra.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(TAG_BITS, bytes, 0, TAMANHO_IV))
            String(cifra.doFinal(bytes, TAMANHO_IV, bytes.size - TAMANHO_IV), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            apagar() // chave perdida (restauração, troca de tela de bloqueio): pede login de novo
            null
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            apagar() // arquivo truncado
            null
        }
    }

    override fun guardar(valor: String) {
        val bytes = try {
            val cifra = Cipher.getInstance(TRANSFORMACAO)
            cifra.init(Cipher.ENCRYPT_MODE, chave())
            cifra.iv + cifra.doFinal(valor.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw IOException("Não foi possível cifrar a sessão", e)
        }
        arquivo.parentFile?.mkdirs()
        arquivo.writeBytes(bytes)
    }

    override fun apagar() {
        arquivo.delete()
    }

    private fun chave(): SecretKey {
        val repositorio = KeyStore.getInstance(REPOSITORIO).apply { load(null) }
        (repositorio.getKey(APELIDO, null) as? SecretKey)?.let { return it }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, REPOSITORIO)
        gerador.init(
            KeyGenParameterSpec.Builder(APELIDO, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gerador.generateKey()
    }

    private companion object {
        const val REPOSITORIO = "AndroidKeyStore"
        const val APELIDO = "painel_ia_cookie"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_IV = 12
        const val TAG_BITS = 128
    }
}
