package io.github.gfvdataweb.painelstatus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.gfvdataweb.painelstatus.ui.AppPainel
import io.github.gfvdataweb.painelstatus.ui.theme.PainelStatusTheme

/** Única Activity do app; as telas são Compose, com navegação por abas. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PainelApp).container
        setContent {
            PainelStatusTheme {
                AppPainel(container, versao = BuildConfig.VERSION_NAME, build = BuildConfig.VERSION_CODE)
            }
        }
    }
}
