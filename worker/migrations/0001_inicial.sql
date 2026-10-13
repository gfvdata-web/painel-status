-- Arquivos do painel guardados como texto, exatamente os bytes recebidos (UTF-8).
-- chave: "status.json", "historico/<slug>.json", "app/versao.json", "controle/agenda.json"
CREATE TABLE arquivos (
  chave         TEXT PRIMARY KEY,
  conteudo      TEXT NOT NULL,
  etag          TEXT NOT NULL,   -- SHA-256 do conteúdo (resposta condicional, 304)
  gerado_em     TEXT,            -- meta.gerado_em, só no status.json
  atualizado_em TEXT NOT NULL
);

-- Retratos do status.json (no máximo 1 a cada 25 min), apagados depois de 30 dias pelo Cron.
CREATE TABLE retratos (
  gerado_em TEXT PRIMARY KEY,
  conteudo  TEXT NOT NULL
);
