package io.github.gfvdataweb.painelstatus.avisos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.gfvdataweb.painelstatus.MainActivity
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.data.avisos.Novidade
import io.github.gfvdataweb.painelstatus.data.avisos.PreferenciasDeAvisos
import io.github.gfvdataweb.painelstatus.data.avisos.doBolao
import io.github.gfvdataweb.painelstatus.data.avisos.principalDoPipeline

/** Mostra as novidades como notificações. Nos testes, o Robolectric guarda o que foi postado. */
interface Avisador {
    fun avisar(novidades: List<Novidade>, preferencias: PreferenciasDeAvisos)

    /** Notificação de exemplo, para conferir se o celular está deixando o app avisar. */
    fun testar()
}

/** Aba que a notificação abre ao ser tocada (extra [EXTRA_ABA] da MainActivity). */
enum class AbaDoAviso { BOLAO, SITES }

const val EXTRA_ABA = "io.github.gfvdataweb.painelstatus.ABA"

/** O Android deixa o app notificar? (Android 13+ pede permissão; antes, sempre pode.) */
fun podeNotificar(contexto: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

class AvisadorDeNotificacoes(private val contexto: Context) : Avisador {

    private val gerenciador = contexto.getSystemService(NotificationManager::class.java)

    override fun avisar(novidades: List<Novidade>, preferencias: PreferenciasDeAvisos) {
        if (novidades.isEmpty()) return
        criarCanais()
        if (preferencias.bolao) {
            val doBolao = novidades.filter { it.doBolao }
            principalDoPipeline(doBolao)?.let { mostrar(ID_PIPELINE, CANAL_BOLAO, AbaDoAviso.BOLAO, textoDoPipeline(it)) }
            doBolao.filterIsInstance<Novidade.SiteForaDoAr>().forEach {
                mostrar(
                    ID_BOLAO_FORA_DO_AR,
                    CANAL_BOLAO,
                    AbaDoAviso.BOLAO,
                    contexto.getString(R.string.aviso_fora_do_ar_titulo, it.nome) to contexto.getString(R.string.aviso_fora_do_ar_texto),
                )
            }
        }
        if (preferencias.sites) {
            novidades.filterIsInstance<Novidade.NovoAlerta>().forEach {
                // Um aviso por site: o alerta seguinte do mesmo site substitui o anterior.
                mostrar(ID_SITES + (it.slug.hashCode() and 0xFFFF), CANAL_SITES, AbaDoAviso.SITES, it.nome to it.texto)
            }
        }
    }

    override fun testar() {
        criarCanais()
        mostrar(
            ID_TESTE,
            CANAL_BOLAO,
            AbaDoAviso.BOLAO,
            contexto.getString(R.string.aviso_teste_titulo) to contexto.getString(R.string.aviso_teste_texto),
        )
    }

    private fun textoDoPipeline(novidade: Novidade): Pair<String, String> = when (novidade) {
        is Novidade.BolaoComecou -> if (novidade.manual) {
            contexto.getString(R.string.aviso_manual_titulo) to contexto.getString(R.string.aviso_comecou_texto)
        } else {
            contexto.getString(R.string.aviso_comecou_titulo) to contexto.getString(R.string.aviso_comecou_texto)
        }
        Novidade.BolaoAguardandoResultado ->
            contexto.getString(R.string.aviso_aguardando_titulo) to contexto.getString(R.string.aviso_aguardando_texto)
        is Novidade.BolaoPaginaAtualizada -> contexto.getString(R.string.aviso_pagina_titulo) to (
            novidade.rodada?.let { contexto.getString(R.string.aviso_pagina_texto_rodada, it) }
                ?: contexto.getString(R.string.aviso_pagina_texto)
            )
        is Novidade.BolaoFalhou -> contexto.getString(R.string.aviso_falhou_titulo) to (
            novidade.etapa?.let { contexto.getString(R.string.aviso_falhou_texto_etapa, it) }
                ?: contexto.getString(R.string.aviso_falhou_texto)
            )
        is Novidade.SiteForaDoAr, is Novidade.NovoAlerta -> error("não é do pipeline: $novidade")
    }

    private fun criarCanais() {
        gerenciador.createNotificationChannel(
            NotificationChannel(CANAL_BOLAO, contexto.getString(R.string.canal_bolao), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = contexto.getString(R.string.canal_bolao_descricao) },
        )
        gerenciador.createNotificationChannel(
            NotificationChannel(CANAL_SITES, contexto.getString(R.string.canal_sites), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = contexto.getString(R.string.canal_sites_descricao) },
        )
    }

    private fun mostrar(id: Int, canal: String, aba: AbaDoAviso, texto: Pair<String, String>) {
        // Checagem aqui mesmo (e não só em podeNotificar) para o lint ver a permissão antes do notify.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val abrir = Intent(contexto, MainActivity::class.java)
            .putExtra(EXTRA_ABA, aba.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val aoTocar = PendingIntent.getActivity(
            contexto,
            id,
            abrir,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (titulo, corpo) = texto
        val notificacao = Notification.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(contexto.getColor(R.color.acento))
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(Notification.BigTextStyle().bigText(corpo))
            .setContentIntent(aoTocar)
            .setAutoCancel(true)
            .build()
        gerenciador.notify(id, notificacao)
    }

    companion object {
        const val CANAL_BOLAO = "bolao"
        const val CANAL_SITES = "sites"
        const val ID_PIPELINE = 1
        const val ID_BOLAO_FORA_DO_AR = 2
        const val ID_TESTE = 3
        const val ID_SITES = 1000
    }
}
