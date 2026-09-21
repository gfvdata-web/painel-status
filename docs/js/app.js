// Painel de Status — lê docs/dados/status.json e monta a página.
// Sem build step: é só fetch + template strings, igual aos outros painéis da família.

let statusAtual = null;

async function carregar() {
  const resp = await fetch("dados/status.json", { cache: "no-store" });
  const status = await resp.json();
  statusAtual = status;

  document.getElementById("meta-gerado").textContent =
    `Atualizado em ${formatarDataHora(status.meta.gerado_em)}`;

  renderizarTudo(status);
}

function renderizarTudo(status) {
  const destaque = status.sites.find((s) => s.destaque);
  const outros = status.sites.filter((s) => !s.destaque);

  if (destaque) renderizarDestaque(destaque);
  renderizarGrade(outros);
}

// ---------- Tema claro/escuro ----------
function temaEfetivo() {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "dark" || attr === "light") return attr;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function sincronizarSwitchTema() {
  const efetivo = temaEfetivo();
  document.querySelectorAll("#tema-switch .tema-switch__btn").forEach((botao) => {
    const ativo = botao.dataset.tema === efetivo;
    botao.classList.toggle("tema-switch__btn--ativo", ativo);
    botao.setAttribute("aria-pressed", ativo ? "true" : "false");
  });
}

function aplicarTema(tema) {
  document.documentElement.setAttribute("data-theme", tema);
  try { localStorage.setItem("tema", tema); } catch (e) { /* modo privado / storage bloqueado */ }
  sincronizarSwitchTema();
  // Canvas (Chart.js) não reage sozinho à troca de tema via CSS — redesenha os gráficos.
  if (statusAtual) renderizarTudo(statusAtual);
}

function configurarTema() {
  sincronizarSwitchTema();
  document.querySelectorAll("#tema-switch .tema-switch__btn").forEach((botao) => {
    botao.addEventListener("click", () => aplicarTema(botao.dataset.tema));
  });
  // Sem escolha explícita salva, acompanha a mudança de tema do sistema.
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (!document.documentElement.getAttribute("data-theme")) {
      sincronizarSwitchTema();
      if (statusAtual) renderizarTudo(statusAtual);
    }
  });
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

function formatarDataCurta(iso) {
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}

function renderizarGraficoAcesso(canvasId, serieDiaria, cor) {
  const ctx = document.getElementById(canvasId);
  if (!ctx || !window.Chart) return;
  new Chart(ctx, {
    type: "line",
    data: {
      labels: serieDiaria.map((d) => d.data),
      datasets: [
        {
          label: "Visitantes únicos por dia",
          data: serieDiaria.map((d) => d.visitantes),
          borderColor: cor,
          backgroundColor: cor + "1f",
          tension: 0.3,
          fill: true,
          pointRadius: 0,
          pointHoverRadius: 4,
          pointHoverBackgroundColor: cor,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (itens) => formatarDataCurta(itens[0].label),
            label: (item) => `${item.formattedValue} visitantes únicos`,
          },
        },
      },
      scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
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
            <p class="rotulo">Visitantes (únicos)</p>
            <p class="valor">${acesso ? acesso.visitantes_unicos ?? "—" : "—"}</p>
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
    renderizarGraficoAcesso(idGrafico, acesso.serie_diaria, "#e10600");
  }
}

function renderizarGrade(sites) {
  const grade = document.getElementById("grade-sites");
  grade.innerHTML = sites.map((site, indice) => {
    const commit = site.ultimo_commit_dados || {};
    const acesso = site.acesso;
    const meta = site.meta_publicada;
    const temSerie = acesso && acesso.serie_diaria && acesso.serie_diaria.length;
    const idGrafico = `grafico-site-${indice}`;

    return `
      <div class="site-card">
        <h3>${site.nome}</h3>
        <div>${badgeNoAr(site)}</div>
        <div class="linha"><span>Dados atualizados</span><strong>${formatarRelativo(commit.data)}</strong></div>
        ${meta && meta.periodo ? `<div class="linha"><span>Período coberto</span><strong>${meta.periodo.inicio} → ${meta.periodo.fim}</strong></div>` : ""}
        <div class="linha"><span>Visitantes (únicos)</span><strong>${acesso ? acesso.visitantes_unicos ?? "—" : "sem rastreio"}</strong></div>
        ${temSerie ? `<div class="grafico-mini"><canvas id="${idGrafico}"></canvas></div>` : ""}
        <a class="link-site" href="${site.pages_url}" target="_blank" rel="noopener">Abrir site →</a>
      </div>
    `;
  }).join("");

  sites.forEach((site, indice) => {
    const acesso = site.acesso;
    if (acesso && acesso.serie_diaria && acesso.serie_diaria.length) {
      renderizarGraficoAcesso(`grafico-site-${indice}`, acesso.serie_diaria, "#2563eb");
    }
  });
}

configurarTema();
carregar().catch((erro) => {
  console.error("Falha ao carregar status.json", erro);
  document.getElementById("meta-gerado").textContent = "Erro ao carregar status.json";
});
