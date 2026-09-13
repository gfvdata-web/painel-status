// Painel de Status — lê docs/dados/status.json e monta a página.
// Sem build step: é só fetch + template strings, igual aos outros painéis da família.

async function carregar() {
  const resp = await fetch("dados/status.json", { cache: "no-store" });
  const status = await resp.json();

  document.getElementById("meta-gerado").textContent =
    `Atualizado em ${formatarDataHora(status.meta.gerado_em)}`;

  const destaque = status.sites.find((s) => s.destaque);
  const outros = status.sites.filter((s) => !s.destaque);

  if (destaque) renderizarDestaque(destaque);
  renderizarGrade(outros);
}

function formatarDataHora(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

function formatarRelativo(iso) {
  if (!iso) return "sem registro";
  const diffMs = Date.now() - new Date(iso).getTime();
  const dias = Math.floor(diffMs / 86400000);
  if (dias <= 0) return "hoje";
  if (dias === 1) return "há 1 dia";
  return `há ${dias} dias`;
}

function badgeStatusPipeline(site) {
  const execucao = site.ultima_execucao_pipeline;
  if (site.em_vigilia) {
    return `<span class="badge aviso" title="Aguardando o resultado oficial sair na Jolpica">Em vigília — aguardando resultado</span>`;
  }
  if (!execucao || execucao.erro) {
    return `<span class="badge neutro">Sem execução registrada</span>`;
  }
  if (execucao.conclusao === "success") {
    return `<span class="badge ok">Última execução OK</span>`;
  }
  if (execucao.conclusao === "failure") {
    return `<a href="${execucao.url}" target="_blank" rel="noopener" style="text-decoration:none">
      <span class="badge erro">Última execução falhou</span>
    </a>`;
  }
  return `<span class="badge neutro">${execucao.status}</span>`;
}

function badgeNoAr(site) {
  const ok = site.site_no_ar && site.site_no_ar.ok;
  return ok
    ? `<span class="badge ok">Site no ar</span>`
    : `<span class="badge erro">Site pode estar fora do ar</span>`;
}

function renderizarGraficoAcesso(canvasId, serieDiaria) {
  const ctx = document.getElementById(canvasId);
  if (!ctx || !window.Chart) return;
  new Chart(ctx, {
    type: "line",
    data: {
      labels: serieDiaria.map((d) => d.data),
      datasets: [
        {
          label: "Visitas por dia",
          data: serieDiaria.map((d) => d.visitas),
          borderColor: "#e10600",
          backgroundColor: "rgba(225, 6, 0, .12)",
          tension: 0.3,
          fill: true,
          pointRadius: 0,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: { y: { beginAtZero: true } },
    },
  });
}

function renderizarDestaque(site) {
  const secao = document.getElementById("secao-destaque");
  const commit = site.ultimo_commit_dados || {};
  const acesso = site.acesso;

  const idGrafico = "grafico-destaque";
  const graficoHtml = acesso && acesso.serie_diaria && acesso.serie_diaria.length
    ? `<canvas id="${idGrafico}"></canvas>`
    : `<div class="sem-dados">Rastreio de acesso ainda não configurado para este site.</div>`;

  secao.innerHTML = `
    <div class="card-destaque">
      <div>
        <div class="titulo-destaque">
          <h2>${site.nome}</h2>
          <span class="selo-destaque">Destaque</span>
        </div>
        <div>
          ${badgeNoAr(site)}
          ${badgeStatusPipeline(site)}
        </div>
        <div class="kpis-destaque">
          <div class="kpi">
            <p class="rotulo">Rodada mais recente</p>
            <p class="valor">${site.rodada_mais_recente ?? "—"}</p>
          </div>
          <div class="kpi">
            <p class="rotulo">Visitas (total)</p>
            <p class="valor">${acesso ? acesso.total_pageviews ?? "—" : "—"}</p>
          </div>
          <div class="kpi">
            <p class="rotulo">Dados atualizados</p>
            <p class="valor" style="font-size:1rem">${formatarRelativo(commit.data)}</p>
          </div>
        </div>
        <p class="commit-info">
          Último commit em dados: "${commit.mensagem ?? "—"}"
          ${commit.url ? `(<a href="${commit.url}" target="_blank" rel="noopener">${commit.sha}</a>)` : ""}
        </p>
        <a class="link-site" href="${site.pages_url}" target="_blank" rel="noopener">Abrir o bolão →</a>
      </div>
      <div class="grafico-caixa">${graficoHtml}</div>
    </div>
  `;

  if (acesso && acesso.serie_diaria && acesso.serie_diaria.length) {
    renderizarGraficoAcesso(idGrafico, acesso.serie_diaria);
  }
}

function renderizarGrade(sites) {
  const grade = document.getElementById("grade-sites");
  grade.innerHTML = sites.map((site) => {
    const commit = site.ultimo_commit_dados || {};
    const acesso = site.acesso;
    const meta = site.meta_publicada;

    return `
      <div class="site-card">
        <h3>${site.nome}</h3>
        <div>${badgeNoAr(site)}</div>
        <div class="linha"><span>Dados atualizados</span><strong>${formatarRelativo(commit.data)}</strong></div>
        ${meta && meta.periodo ? `<div class="linha"><span>Período coberto</span><strong>${meta.periodo.inicio} → ${meta.periodo.fim}</strong></div>` : ""}
        <div class="linha"><span>Visitas (total)</span><strong>${acesso ? acesso.total_pageviews ?? "—" : "sem rastreio"}</strong></div>
        <a class="link-site" href="${site.pages_url}" target="_blank" rel="noopener">Abrir site →</a>
      </div>
    `;
  }).join("");
}

carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("meta-gerado").textContent = "Erro ao carregar status.json";
});
