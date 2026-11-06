package com.dostup.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dostup.app.BuildConfig
import com.dostup.app.awg.Obfuscation
import com.dostup.app.awg.QuicMask
import com.dostup.app.data.DnsOption
import com.dostup.app.data.Store
import com.dostup.app.tile.ToggleTileService
import com.dostup.app.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.config.Config
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

private val ENDPOINT_PATTERN = Regex("""^(\[[0-9a-fA-F:]+]|[\w.\-]+):\d{1,5}$""")

@Composable
fun SettingsScreen(vpn: VpnController, store: Store) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle()
    var customConfig by remember { mutableStateOf(store.customConfig) }
    var endpointText by rememberSaveable { mutableStateOf(settings.manualEndpoint ?: "162.159.192.1:2408") }
    var manualEndpoint by remember { mutableStateOf(settings.manualEndpoint != null) }
    var tileAdded by remember { mutableStateOf(store.tileAdded) }
    var confirmReset by remember { mutableStateOf(false) }
    var editSni by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    fun withConfig(action: (String) -> Unit) {
        scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { vpn.exportConfig() } }
            text.onSuccess(action).onFailure { toast(context, "Не удалось получить конфиг: ${it.message}") }
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.bgBase)) {
        TopAppBar(title = "Настройки", scrollBehavior = scrollBehavior)
        LazyColumn(
            Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        ) {
            if (customConfig != null) {
                item {
                    SectionCard(Modifier.padding(top = 12.dp)) {
                        BasicComponent(
                            title = "Используется свой конфиг",
                            summary = "WARP, маскировка и подбор сервера отключены",
                            insideMargin = RowMargin,
                        )
                        RowDivider()
                        SuperArrow(
                            title = "Вернуться к WARP",
                            titleColor = BasicComponentDefaults.titleColor(color = Palette.brand),
                            insideMargin = RowMargin,
                            onClick = {
                                store.customConfig = null
                                customConfig = null
                            },
                        )
                    }
                }
            }

            item {
                SectionTitle("Шторка уведомлений")
                SectionCard {
                    SuperArrow(
                        title = "Плитка «ByeByeRKN»",
                        summary = if (tileAdded) "Добавлена в шторку" else "Добавить в панель быстрых настроек",
                        insideMargin = RowMargin,
                        onClick = {
                            if (tileAdded) return@SuperArrow
                            val asked = ToggleTileService.requestAdd(context) { added ->
                                tileAdded = added
                                if (added) toast(context, "Плитка добавлена в шторку")
                            }
                            if (!asked) toast(context, "Откройте шторку, нажмите карандаш и перетащите плитку «ByeByeRKN» в панель")
                        },
                    )
                }
            }

            item {
                SectionTitle("Конфиг для AmneziaWG")
                SectionCard {
                    SuperArrow(
                        title = "Поделиться конфигом",
                        summary = "Файл byebyerkn.conf",
                        insideMargin = RowMargin,
                        onClick = { withConfig { shareConfig(context, it) } },
                    )
                    RowDivider()
                    SuperArrow(
                        title = "Скопировать конфиг",
                        insideMargin = RowMargin,
                        onClick = {
                            withConfig {
                                copyToClipboard(context, it)
                                toast(context, "Конфиг скопирован")
                            }
                        },
                    )
                    RowDivider()
                    SuperArrow(
                        title = "Импортировать из буфера",
                        summary = "Свой сервер вместо WARP",
                        insideMargin = RowMargin,
                        onClick = {
                            val text = readClipboard(context)
                            val error = validateConfig(text)
                            if (error != null) {
                                toast(context, error)
                            } else {
                                store.customConfig = text
                                customConfig = text
                                toast(context, "Конфиг импортирован")
                            }
                        },
                    )
                }
            }

            item {
                SectionTitle("Для опытных")
                SectionCard {
                    SuperArrow(
                        title = if (showAdvanced) "Скрыть ручные настройки" else "Показать ручные настройки",
                        summary = "Обычно не нужны: приложение подбирает всё само",
                        insideMargin = RowMargin,
                        onClick = { showAdvanced = !showAdvanced },
                    )
                }
            }

            if (showAdvanced) {
                item {
                    SectionTitle("Подключение")
                    SectionCard {
                        val obfuscations = listOf<Obfuscation?>(null) + Obfuscation.entries
                        SuperDropdown(
                            title = "Маскировка",
                            summary = settings.obfuscation?.summary ?: "Подбирается под вашу сеть",
                            items = obfuscations.map { it?.title ?: "Авто" },
                            selectedIndex = obfuscations.indexOf(settings.obfuscation),
                            insideMargin = RowMargin,
                            onSelectedIndexChange = { index -> store.updateSettings { it.copy(obfuscation = obfuscations[index]) } },
                        )
                        RowDivider()
                        SuperDropdown(
                            title = "Сервер",
                            summary = if (manualEndpoint) "Свой адрес WARP" else "Подбирается автоматически",
                            items = listOf("Авто", "Вручную"),
                            selectedIndex = if (manualEndpoint) 1 else 0,
                            insideMargin = RowMargin,
                            onSelectedIndexChange = { index ->
                                manualEndpoint = index == 1
                                store.updateSettings {
                                    it.copy(manualEndpoint = if (manualEndpoint && ENDPOINT_PATTERN.matches(endpointText)) endpointText else null)
                                }
                            },
                        )
                        if (manualEndpoint) {
                            TextField(
                                value = endpointText,
                                onValueChange = { value ->
                                    endpointText = value.trim()
                                    if (ENDPOINT_PATTERN.matches(endpointText)) {
                                        store.updateSettings { it.copy(manualEndpoint = endpointText) }
                                    }
                                },
                                label = "Адрес:порт",
                                singleLine = true,
                                backgroundColor = Palette.bgSheet,
                                borderColor = if (ENDPOINT_PATTERN.matches(endpointText)) Palette.brand else Palette.danger,
                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                            )
                        }
                        RowDivider()
                        SuperDropdown(
                            title = "DNS",
                            summary = settings.dns.servers.take(2).joinToString(),
                            items = DnsOption.entries.map { it.title },
                            selectedIndex = settings.dns.ordinal,
                            insideMargin = RowMargin,
                            onSelectedIndexChange = { index -> store.updateSettings { it.copy(dns = DnsOption.entries[index]) } },
                        )
                        RowDivider()
                        SuperArrow(
                            title = "Сайт для QUIC-маски",
                            summary = settings.quicSni.ifBlank { "Случайный популярный сайт" },
                            insideMargin = RowMargin,
                            onClick = { editSni = true },
                        )
                        RowDivider()
                        SuperArrow(
                            title = "Подобрать сервер заново",
                            summary = "При следующем подключении",
                            insideMargin = RowMargin,
                            onClick = {
                                vpn.forgetServer()
                                toast(context, "При следующем подключении сервер подберётся заново")
                            },
                        )
                    }
                }

                item {
                    SectionTitle("Аккаунт WARP")
                    SectionCard {
                        SuperArrow(
                            title = "Зарегистрироваться заново",
                            summary = if (store.account != null) "Новый ключ и адрес WARP" else "Регистрация будет при первом подключении",
                            insideMargin = RowMargin,
                            onClick = { confirmReset = true },
                        )
                    }
                }
            }

            item {
                SectionTitle("О приложении")
                SectionCard {
                    BasicComponent(
                        title = "ByeByeRKN ${BuildConfig.VERSION_NAME}",
                        summary = "AmneziaWG ${vpn.awgVersion}",
                        insideMargin = RowMargin,
                    )
                    RowDivider()
                    BasicComponent(
                        title = "Лицензии",
                        summary = "amneziawg-android и amneziawg-go (Apache-2.0 / MIT), на основе WireGuard®. " +
                            "Не связано с Cloudflare; WARP — сервис Cloudflare, Inc.",
                        insideMargin = RowMargin,
                    )
                }
            }
        }
    }

    SniDialog(
        show = editSni,
        current = settings.quicSni,
        onDismiss = { editSni = false },
        onSave = { value ->
            editSni = false
            store.updateSettings { it.copy(quicSni = value.trim()) }
        },
    )

    SuperDialog(
        show = confirmReset,
        title = "Новая регистрация",
        summary = "Будет создан новый ключ и адрес WARP. Старые экспортированные конфиги продолжат работать, пока их не отключит Cloudflare.",
        onDismissRequest = { confirmReset = false },
    ) {
        DialogButtons(
            confirm = "Сбросить",
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                vpn.resetAccount()
                toast(context, "Новая регистрация будет при следующем подключении")
            },
        )
    }
}

@Composable
private fun SniDialog(show: Boolean, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by remember(show) { mutableStateOf(current) }
    SuperDialog(
        show = show,
        title = "Сайт для QUIC-маски",
        summary = "Первый пакет будет похож на HTTP/3-запрос к этому сайту. " +
            "Пусто — случайный из ${QuicMask.defaultSni.take(3).joinToString()} и других.",
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = draft,
            onValueChange = { draft = it },
            label = "ya.ru",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        DialogButtons(confirm = "Сохранить", onDismiss = onDismiss, onConfirm = { onSave(draft) })
    }
}

@Composable
private fun DialogButtons(confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(text = "Отмена", onClick = onDismiss, modifier = Modifier.weight(1f))
        TextButton(
            text = confirm,
            onClick = onConfirm,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun validateConfig(text: String?): String? {
    if (text.isNullOrBlank()) return "В буфере обмена нет текста"
    return try {
        Config.parse(BufferedReader(StringReader(text)))
        null
    } catch (e: Exception) {
        "Это не похоже на конфиг WireGuard/AmneziaWG: ${e.message}"
    }
}

private fun shareConfig(context: Context, text: String) {
    val dir = File(context.cacheDir, "export").apply { mkdirs() }
    val file = File(dir, "byebyerkn.conf").apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Поделиться конфигом"))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("byebyerkn.conf", text))
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
