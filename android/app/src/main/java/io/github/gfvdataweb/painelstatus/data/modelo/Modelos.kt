package io.github.gfvdataweb.painelstatus.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Espelho de docs/dados/status.json (formato na docstring de src/publicacao.py,
// detalhes em src/detalhes_site.py e src/etapas_bolao.py) e de
// docs/dados/historico/<slug>.json (src/historico.py). Nomes do JSON em
// @SerialName; todo campo que não identifica o item tem padrão, e o leitor
// ignora campos novos e troca null por padrão: o painel pode acrescentar
// campos sem quebrar o app.

@Serializable
data class Status(
    val meta: Meta = Meta(),
    val sites: List<Site> = emptyList(),
    @SerialName("forms_avulsos") val formsAvulsos: List<FormAvulso> = emptyList(),
)

@Serializable
data class Meta(
    /** Quando o Action gerou este status.json (ISO, UTC). */
    @SerialName("gerado_em") val geradoEm: String? = null,
)

@Serializable
data class Site(
    val slug: String,
    val nome: String,
    val repo: String = "",
    @SerialName("pages_url") val pagesUrl: String = "",
    /** O site em destaque (Bolão F1) tem card e aba próprios. */
    val destaque: Boolean = false,
    @SerialName("form_url") val formUrl: String? = null,
    @SerialName("form_rotulo") val formRotulo: String? = null,
    @SerialName("ultimo_commit_dados") val ultimoCommitDados: Commit? = null,
    @SerialName("site_no_ar") val siteNoAr: SiteNoAr? = null,
    @SerialName("periodo_publicado") val periodoPublicado: Periodo? = null,
    /** Última execução do workflow cadastrado (coleta ou pipeline do Bolão). */
    @SerialName("ultima_execucao_pipeline") val ultimaExecucao: Execucao? = null,
    val acesso: Acesso? = null,
    // Só no site com pipeline do Bolão F1:
    @SerialName("rodada_mais_recente") val rodadaMaisRecente: Int? = null,
    @SerialName("em_vigilia") val emVigilia: Boolean = false,
    @SerialName("etapas_pipeline") val etapasPipeline: List<Etapa> = emptyList(),
    // Nos demais sites (pop-up de detalhes da página):
    val detalhes: Detalhes? = null,
    val alertas: List<Alerta> = emptyList(),
    /** Caminho do histórico acumulado, relativo à raiz do painel (`dados/historico/<slug>.json`). */
    @SerialName("historico_arquivo") val historicoArquivo: String? = null,
)

/** Um commit, ou só `erro` quando a API do GitHub não respondeu. */
@Serializable
data class Commit(
    val data: String? = null,
    val mensagem: String? = null,
    val sha: String? = null,
    val url: String? = null,
    val erro: String? = null,
)

@Serializable
data class SiteNoAr(
    val ok: Boolean = false,
    @SerialName("status_http") val statusHttp: Int? = null,
    val erro: String? = null,
)

@Serializable
data class Periodo(val inicio: String? = null, val fim: String? = null)

/**
 * Uma execução do GitHub Actions. Serve para a última execução, o histórico
 * recente do pop-up, o último deploy do Pages (sem `id`) e as execuções do
 * histórico acumulado (sem `status`, com `avisos`). Só `erro` quando a API falhou.
 */
@Serializable
data class Execucao(
    val id: Long? = null,
    val evento: String? = null,
    /** queued | in_progress | completed (ausente no histórico acumulado: lá todas terminaram). */
    val status: String? = null,
    /** success | failure | cancelled | timed_out | startup_failure | skipped… (null enquanto roda). */
    val conclusao: String? = null,
    @SerialName("criado_em") val criadoEm: String? = null,
    @SerialName("atualizado_em") val atualizadoEm: String? = null,
    val url: String? = null,
    @SerialName("duracao_s") val duracaoS: Long? = null,
    /** Só nas falhas: onde e por que falhou (linha de erro do log). */
    val motivo: Motivo? = null,
    /** Só no histórico acumulado: anotações da execução; null = não deu para ler. */
    val avisos: List<Aviso>? = null,
    val erro: String? = null,
)

@Serializable
data class Motivo(val job: String? = null, val passo: String? = null, val mensagem: String? = null)

@Serializable
data class Aviso(val nivel: String = "", val mensagem: String = "")

/** Uma etapa do passo a passo do Bolão F1 (src/etapas_bolao.py). */
@Serializable
data class Etapa(
    val chave: String = "",
    val titulo: String = "",
    /** ok | andamento | aguardando | erro | pendente | pulado */
    val estado: String = "",
    val detalhe: String = "",
    val quando: String? = null,
)

@Serializable
data class Acesso(
    @SerialName("visitantes_unicos") val visitantesUnicos: Int? = null,
    @SerialName("serie_diaria") val serieDiaria: List<VisitasDoDia> = emptyList(),
)

@Serializable
data class VisitasDoDia(val data: String = "", val visitantes: Int = 0)

@Serializable
data class Detalhes(
    /** null = site sem workflow de coleta cadastrado. */
    val coleta: Coleta? = null,
    @SerialName("commits_dados") val commitsDados: List<Commit> = emptyList(),
    @SerialName("deploy_pages") val deployPages: Execucao? = null,
)

@Serializable
data class Coleta(
    val workflow: String = "",
    @SerialName("url_workflow") val urlWorkflow: String? = null,
    val agenda: List<Agenda> = emptyList(),
    /** Execuções recentes, mais recente primeiro. */
    val historico: List<Execucao> = emptyList(),
    @SerialName("ultimo_sucesso") val ultimoSucesso: Execucao? = null,
    @SerialName("ultima_falha") val ultimaFalha: Execucao? = null,
    @SerialName("passos_ultima") val passosUltima: List<Passo> = emptyList(),
    val erro: String? = null,
)

@Serializable
data class Agenda(val cron: String = "", val descricao: String = "")

@Serializable
data class Passo(val nome: String = "", val conclusao: String? = null)

@Serializable
data class Alerta(
    /** aviso | erro */
    val nivel: String = "aviso",
    val texto: String = "",
)

@Serializable
data class FormAvulso(val nome: String, val url: String)

/** docs/dados/historico/<slug>.json */
@Serializable
data class Historico(
    val slug: String = "",
    val nome: String = "",
    val workflow: String = "",
    @SerialName("url_workflow") val urlWorkflow: String? = null,
    @SerialName("atualizado_em") val atualizadoEm: String? = null,
    /** Mais recente primeiro. */
    val execucoes: List<Execucao> = emptyList(),
)
