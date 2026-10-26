package io.github.gfvdataweb.painelstatus.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.gfvdataweb.painelstatus.R
import io.github.gfvdataweb.painelstatus.ui.painel.ErroDeLogin
import io.github.gfvdataweb.painelstatus.ui.painel.EstadoDaOrdem

/**
 * Ações da barra superior da aba Status: o menu (Entrar / Editar ordem / Sair) ou,
 * durante a edição, Cancelar e Salvar.
 */
@Composable
fun AcoesDaOrdem(
    estado: EstadoDaOrdem,
    aoEntrar: () -> Unit,
    aoEditar: () -> Unit,
    aoSair: () -> Unit,
    aoSalvar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    if (estado.editando) {
        TextButton(onClick = aoCancelar, enabled = !estado.salvando) { Text(stringResource(R.string.ordem_cancelar)) }
        TextButton(onClick = aoSalvar, enabled = !estado.salvando) { Text(stringResource(R.string.ordem_salvar)) }
        return
    }
    var aberto by remember { mutableStateOf(false) }
    IconButton(onClick = { aberto = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.ordem_menu))
    }
    DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
        if (estado.logada) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ordem_editar)) },
                onClick = {
                    aberto = false
                    aoEditar()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ordem_sair)) },
                onClick = {
                    aberto = false
                    aoSair()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ordem_entrar)) },
                onClick = {
                    aberto = false
                    aoEntrar()
                },
            )
        }
    }
}

/** Usuário e senha; a senha só passa por aqui (nunca é guardada). */
@Composable
fun DialogoDeLogin(
    erro: ErroDeLogin?,
    entrando: Boolean,
    aoEntrar: (usuario: String, senha: String) -> Unit,
    aoFechar: () -> Unit,
) {
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(stringResource(R.string.ordem_login_titulo)) },
        text = {
            Column {
                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text(stringResource(R.string.ordem_usuario)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = senha,
                    onValueChange = { senha = it },
                    label = { Text(stringResource(R.string.ordem_senha)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (erro != null) {
                    Text(
                        stringResource(if (erro == ErroDeLogin.INCORRETO) R.string.ordem_login_incorreto else R.string.ordem_login_falhou),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoEntrar(usuario, senha) }, enabled = !entrando && usuario.isNotBlank() && senha.isNotEmpty()) {
                Text(stringResource(R.string.ordem_entrar))
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text(stringResource(R.string.ordem_cancelar)) } },
    )
}
