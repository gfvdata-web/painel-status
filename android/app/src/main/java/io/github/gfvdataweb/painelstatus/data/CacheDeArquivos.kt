package io.github.gfvdataweb.painelstatus.data

import java.io.File

/** Texto guardado no cache e quando foi salvo (epoch em ms). */
data class TextoGuardado(val texto: String, val salvoEm: Long)

/**
 * Cache offline: guarda cada JSON baixado em um arquivo, com o mesmo caminho
 * relativo do painel (`dados/status.json`). A gravação é atômica (arquivo
 * temporário + rename), então uma interrupção nunca deixa JSON pela metade.
 */
class CacheDeArquivos(private val pasta: File) {

    fun ler(caminho: String): TextoGuardado? {
        val arquivo = arquivoDe(caminho)
        if (!arquivo.isFile) return null
        return TextoGuardado(arquivo.readText(), arquivo.lastModified())
    }

    fun salvar(caminho: String, texto: String, salvoEm: Long) {
        val arquivo = arquivoDe(caminho)
        arquivo.parentFile?.mkdirs()
        val temporario = File(arquivo.parentFile, arquivo.name + ".tmp")
        temporario.writeText(texto)
        temporario.setLastModified(salvoEm)
        if (!temporario.renameTo(arquivo)) {
            arquivo.delete()
            check(temporario.renameTo(arquivo)) { "Não foi possível gravar $caminho no cache" }
        }
    }

    private fun arquivoDe(caminho: String): File {
        val arquivo = File(pasta, caminho).canonicalFile
        require(arquivo.path.startsWith(pasta.canonicalPath)) { "Caminho fora do cache: $caminho" }
        return arquivo
    }
}
