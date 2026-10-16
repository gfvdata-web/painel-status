package io.github.gfvdataweb.painelstatus.data.ia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// Modelos dos JSONs do painel de IA (ia.gfvdata.com, repo painel-ia): uso.json,
// limites.json e catalogo.json. Lidos com `leitorJson` (campos novos são
// ignorados e null vira o padrão). Tudo opcional: o coletor roda no PC do
// Guilherme e pode deixar de publicar qualquer campo.

/** Lê texto ou número JSON como texto (ex.: `"vezes": "2"` ou `"vezes": 2`); null vira vazio. */
object TextoOuNumeroSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TextoOuNumero", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val elemento = decoder.decodeJsonElement()
        return if (elemento is JsonPrimitive && elemento !is JsonNull) elemento.content else ""
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
data class MetaIa(
    @SerialName("gerado_em") val geradoEm: String = "",
    val provedor: String = "",
)

/** Consumo de um modelo (por dia, por sessão ou por agente). */
@Serializable
data class UsoModelo(
    val entrada: Long = 0,
    val saida: Long = 0,
    @SerialName("cache_leitura") val cacheLeitura: Long = 0,
    @SerialName("cache_escrita") val cacheEscrita: Long = 0,
    val respostas: Long = 0,
    val total: Long = 0,
    @SerialName("custo_usd") val custoUsd: Double = 0.0,
)

/** Uma linha de `uso.json` → `dias`: um modelo num dia (AAAA-MM-DD, dia local do coletor). */
@Serializable
data class DiaUso(
    val dia: String = "",
    val modelo: String = "",
    val entrada: Long = 0,
    val saida: Long = 0,
    @SerialName("cache_leitura") val cacheLeitura: Long = 0,
    @SerialName("cache_escrita") val cacheEscrita: Long = 0,
    val respostas: Long = 0,
    val total: Long = 0,
    @SerialName("custo_usd") val custoUsd: Double = 0.0,
)

@Serializable
data class AgenteUso(
    val modelos: Map<String, UsoModelo> = emptyMap(),
    val total: Long = 0,
    @SerialName("custo_usd") val custoUsd: Double = 0.0,
    val sessoes: Long = 0,
)

@Serializable
data class SessaoUso(
    val id: String = "",
    val titulo: String = "",
    val projeto: String = "",
    val inicio: String = "",
    val fim: String = "",
    val modelos: Map<String, UsoModelo> = emptyMap(),
    /** Tokens por agente ("principal" e os subagentes). */
    val agentes: Map<String, Long> = emptyMap(),
    val total: Long = 0,
    @SerialName("custo_usd") val custoUsd: Double = 0.0,
)

@Serializable
data class UsoIa(
    val meta: MetaIa = MetaIa(),
    val dias: List<DiaUso> = emptyList(),
    val agentes: Map<String, AgenteUso> = emptyMap(),
    val sessoes: List<SessaoUso> = emptyList(),
)

/** Uma janela de limite do plano. Tempos em segundos desde 1970; `zera_em` é null quando a leitura não trouxe. */
@Serializable
data class FaixaLimite(
    @SerialName("usado_pct") val usadoPct: Double = 0.0,
    @SerialName("zera_em") val zeraEm: Double? = null,
    @SerialName("lido_em") val lidoEm: Double? = null,
    @SerialName("zerou_desde_a_leitura") val zerouDesdeALeitura: Boolean = false,
)

@Serializable
data class LimitesAtuais(
    @SerialName("five_hour") val cincoHoras: FaixaLimite? = null,
    @SerialName("seven_day") val seteDias: FaixaLimite? = null,
)

/** Um ponto do histórico de leituras; `null` = a leitura não trouxe aquela janela. */
@Serializable
data class PontoLimite(
    val ts: Double = 0.0,
    @SerialName("five_hour") val cincoHoras: Double? = null,
    @SerialName("seven_day") val seteDias: Double? = null,
)

@Serializable
data class LimitesIa(
    val meta: MetaIa = MetaIa(),
    val atual: LimitesAtuais? = null,
    val historico: List<PontoLimite> = emptyList(),
)

@Serializable
data class AgenteCatalogo(
    val nome: String = "",
    val modelo: String = "",
    val descricao: String = "",
)

@Serializable
data class SkillCatalogo(
    val nome: String = "",
    val descricao: String = "",
)

/** Ideia de agente/skill ainda não criada (`~/.claude/catalogo/candidatos.md`); `vezes` vem como texto ou número. */
@Serializable
data class CandidatoCatalogo(
    @SerialName("data") val quando: String = "",
    val projeto: String = "",
    val tarefa: String = "",
    val tipo: String = "",
    @Serializable(with = TextoOuNumeroSerializer::class) val vezes: String = "",
)

@Serializable
data class CatalogoIa(
    val meta: MetaIa = MetaIa(),
    val agentes: List<AgenteCatalogo> = emptyList(),
    val skills: List<SkillCatalogo> = emptyList(),
    val candidatos: List<CandidatoCatalogo> = emptyList(),
)

/** Os três arquivos juntos; cada um é null enquanto o coletor não publicou (404). */
data class DadosIa(
    val uso: UsoIa? = null,
    val limites: LimitesIa? = null,
    val catalogo: CatalogoIa? = null,
) {
    /** Data de geração mais recente entre os arquivos (ISO), ou null. */
    val geradoEm: String?
        get() = listOfNotNull(uso?.meta?.geradoEm, limites?.meta?.geradoEm, catalogo?.meta?.geradoEm)
            .filter { it.isNotEmpty() }
            .maxOrNull()

    val vazio: Boolean get() = uso == null && limites == null && catalogo == null
}
