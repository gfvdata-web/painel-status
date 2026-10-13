"""Vigias do Bolão (src/vigia_bolao.py) e do Forms (src/vigia_forms.py): fase, mudança e a saída
no formato do $GITHUB_OUTPUT que o atualizar-status.yml lê com sed."""

import contextlib
import io
import json
import sys
import unittest
from datetime import datetime, timedelta, timezone
from unittest import mock

from tests.apoio import TesteBase, execucao
from src import vigia_bolao as vb
from src import vigia_forms as vf

DESDE = datetime(2026, 10, 13, 10, 0, tzinfo=timezone.utc)
AGORA = datetime(2026, 10, 13, 10, 30, tzinfo=timezone.utc)


def etapa(estado, chave="resultado", detalhe=""):
    return {"chave": chave, "titulo": chave, "estado": estado, "detalhe": detalhe, "quando": None}


def bolao(exec_=None, etapas=(), sha="aaa1111"):
    site = {"slug": vb.SLUG_BOLAO, "etapas_pipeline": list(etapas), "ultimo_commit_dados": {"sha": sha}}
    if exec_ is not None:
        site["ultima_execucao_pipeline"] = exec_
    return site


class FaseDoBolao(unittest.TestCase):
    def test_sem_execucao_nova(self):
        self.assertEqual(vb.fase(None, DESDE, AGORA), "sem_execucao")
        self.assertEqual(vb.fase(bolao(), DESDE, AGORA), "sem_execucao")
        self.assertEqual(vb.fase(bolao({"erro": "x"}), DESDE, AGORA), "sem_execucao")
        antiga = execucao(1, criado="2026-10-13T09:59:59Z")
        self.assertEqual(vb.fase(bolao(antiga), DESDE, AGORA), "sem_execucao")

    def test_vigilia_tem_prioridade(self):
        rodando = execucao(1, status="in_progress", conclusao=None, criado="2026-10-13T10:01:00Z")
        self.assertEqual(vb.fase(bolao(rodando, [etapa("aguardando")]), DESDE, AGORA), "vigilia")

    def test_execucao_rodando_e_andamento(self):
        rodando = execucao(1, status="in_progress", conclusao=None, criado="2026-10-13T10:01:00Z")
        self.assertEqual(vb.fase(bolao(rodando, [etapa("andamento")]), DESDE, AGORA), "andamento")

    def test_deploy_pendente_espera_ate_15_min(self):
        ok = execucao(1, criado="2026-10-13T10:01:00Z", atualizado="2026-10-13T10:10:00Z")
        etapas = [etapa("andamento", "pagina")]
        self.assertEqual(vb.fase(bolao(ok, etapas), DESDE, ok_mais(ok, 14)), "andamento")
        self.assertEqual(vb.fase(bolao(ok, etapas), DESDE, ok_mais(ok, 16)), "concluido")

    def test_tudo_em_estado_final_e_concluido(self):
        ok = execucao(1, criado="2026-10-13T10:01:00Z")
        etapas = [etapa("ok"), etapa("erro", "x"), etapa("pulado", "y"), etapa("pendente", "z")]
        self.assertEqual(vb.fase(bolao(ok, etapas), DESDE, AGORA), "concluido")


def ok_mais(execucao_, minutos):
    return datetime.fromisoformat(execucao_["atualizado_em"].replace("Z", "+00:00")) + timedelta(minutes=minutos)


class AssinaturaDoBolao(unittest.TestCase):
    def test_o_que_muda_o_que_merece_commit(self):
        base = bolao(execucao(1), [etapa("ok")])
        self.assertEqual(vb.assinatura(base), vb.assinatura(bolao(execucao(1), [etapa("ok")])))
        self.assertNotEqual(vb.assinatura(base), vb.assinatura(bolao(execucao(2), [etapa("ok")])))
        self.assertNotEqual(vb.assinatura(base), vb.assinatura(bolao(execucao(1), [etapa("erro")])))
        self.assertNotEqual(vb.assinatura(base), vb.assinatura(bolao(execucao(1), [etapa("ok", detalhe="3 consultas")])))
        self.assertNotEqual(vb.assinatura(base), vb.assinatura(bolao(execucao(1), [etapa("ok")], sha="bbb2222")))
        self.assertIsNone(vb.assinatura(None))

    def test_hora_da_coleta_nao_conta(self):
        # o gerado_em sempre muda: se contasse, cada volta da vigia viraria um commit
        a = bolao(execucao(1), [etapa("ok")])
        b = json.loads(json.dumps(a))
        b["acesso"] = {"visitantes_unicos": 999}
        self.assertEqual(vb.assinatura(a), vb.assinatura(b))


class SaidaDoWorkflow(TesteBase):
    """As linhas `fase=` e `mudou=` que o `sed -n 's/^fase=//p'` do workflow lê."""

    def rodar(self, modulo, atual, anterior, desde="2026-10-13T10:00:00Z"):
        saida = self.tmp / "status.json"
        saida.write_text(json.dumps(atual), encoding="utf-8")
        argv = ["vigia", "--desde", desde, "--anterior", str(self.tmp / "anterior.json")]
        if anterior is not None:
            (self.tmp / "anterior.json").write_text(json.dumps(anterior), encoding="utf-8")
        buffer = io.StringIO()
        with mock.patch.object(modulo, "SAIDA", saida), mock.patch.object(sys, "argv", argv), \
             contextlib.redirect_stdout(buffer):
            modulo.main()
        return dict(linha.split("=") for linha in buffer.getvalue().split())

    def test_bolao_mudou_e_fase(self):
        exec_ = execucao(1, criado="2026-10-13T10:01:00Z")
        atual = {"sites": [bolao(exec_, [etapa("ok")])]}
        anterior = {"sites": [bolao(execucao(1, criado="2026-10-13T10:01:00Z"), [etapa("ok")])]}
        self.assertEqual(self.rodar(vb, atual, anterior), {"fase": "concluido", "mudou": "nao"})
        anterior["sites"][0]["etapas_pipeline"] = [etapa("pendente")]
        self.assertEqual(self.rodar(vb, atual, anterior)["mudou"], "sim")

    def test_bolao_sem_anterior_conta_como_mudou(self):
        atual = {"sites": [bolao(execucao(1, criado="2026-10-13T10:01:00Z"), [etapa("ok")])]}
        self.assertEqual(self.rodar(vb, atual, None)["mudou"], "sim")

    def test_bolao_anterior_quebrado(self):
        atual = {"sites": [bolao()]}
        (self.tmp / "anterior.json").write_text("{quebrado", encoding="utf-8")
        saida = self.tmp / "status.json"
        saida.write_text(json.dumps(atual), encoding="utf-8")
        argv = ["vigia", "--desde", "2026-10-13T10:00:00Z", "--anterior", str(self.tmp / "anterior.json")]
        buffer = io.StringIO()
        with mock.patch.object(vb, "SAIDA", saida), mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(buffer):
            vb.main()
        self.assertIn("fase=sem_execucao", buffer.getvalue())

    def test_forms_fase_e_mudou(self):
        slug = next(iter(vf.SLUGS_FORMS))
        def site(exec_, etapas):
            return {"slug": slug, "pipeline_forms": {"workflow": "p.yml", "execucao": exec_, "etapas": etapas}}
        concluida = execucao(1, criado="2026-10-13T10:01:00Z")
        atual = {"sites": [site(concluida, [etapa("ok", "ficha")])]}
        self.assertEqual(self.rodar(vf, atual, atual), {"fase": "concluido", "mudou": "nao"})
        anterior = {"sites": [site(concluida, [etapa("andamento", "ficha")])]}
        self.assertEqual(self.rodar(vf, atual, anterior)["mudou"], "sim")


class FasesDoForms(unittest.TestCase):
    def status(self, *execs):
        slug = next(iter(vf.SLUGS_FORMS))
        sites = [{"slug": slug, "pipeline_forms": {"workflow": "p.yml", "execucao": e, "etapas": []}} for e in execs[:1]]
        return {"sites": sites}

    def test_fases(self):
        nova = execucao(1, criado="2026-10-13T10:01:00Z")
        self.assertEqual(vf.fase(self.status(nova), DESDE), "concluido")
        self.assertEqual(vf.fase(self.status(execucao(1, status="queued", conclusao=None, criado="2026-10-13T10:01:00Z")), DESDE), "andamento")
        self.assertEqual(vf.fase(self.status(execucao(1, status="in_progress", conclusao=None, criado="2026-10-13T10:01:00Z")), DESDE), "andamento")
        self.assertEqual(vf.fase(self.status(execucao(1, criado="2026-10-13T09:00:00Z")), DESDE), "sem_execucao")
        self.assertEqual(vf.fase(self.status({"erro": "x"}), DESDE), "sem_execucao")
        self.assertEqual(vf.fase({"sites": []}, DESDE), "sem_execucao")

    def test_ignora_sites_sem_pipeline_forms(self):
        self.assertEqual(vf.assinatura({"sites": [{"slug": "bolao_f1", "pipeline_forms": {"execucao": execucao(1)}}]}), {})
        self.assertEqual(vf.assinatura(None), {})

    def test_slug_do_forms_vem_do_cadastro(self):
        self.assertEqual(vf.SLUGS_FORMS, {"plants_care"})
        self.assertEqual(vb.SLUG_BOLAO, "bolao_f1")


if __name__ == "__main__":
    unittest.main()
