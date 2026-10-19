package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.rikkahub.ui.components.ui.rikkaGlassStyle
import me.rerere.rikkahub.ui.components.ui.frostedPlate
import me.rerere.rikkahub.ui.components.ui.frostedPlateSpec
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.costguards.TokenBudgetTracker
import me.rerere.rikkahub.data.usage.TurnFooter
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.datastore.resolveChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.subagent.SubAgentRegistry
import me.rerere.rikkahub.subagent.SubAgentRun
import me.rerere.rikkahub.subagent.SubAgentStatus
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val subAgentRegistry: SubAgentRegistry = koinInject()
    val subAgentRuns by subAgentRegistry.runs.collectAsStateWithLifecycle()
    val subAgentStatus = subAgentStatusLine(subAgentRuns)
    val mergedStatus = listOfNotNull(processingStatus, subAgentStatus)
        .joinToString(separator = "  ·  ")
        .ifBlank { null }

    val deepSeekDrawerState = rememberDeepSeekDrawerState()
    val hazeState = rememberHazeState()
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(deepSeekDrawerState.progress) {
        if (deepSeekDrawerState.progress > 0.1f) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    @Suppress("DEPRECATION")
    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    LaunchedEffect(isBigScreen) {
        if (isBigScreen && deepSeekDrawerState.progress > 0.01f) {
            deepSeekDrawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)
    val inputState = vm.inputState

    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        hazeState = hazeState,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = mergedStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = null,
                    deepSeekDrawerState = deepSeekDrawerState,
                    onOpenDrawer = { scope.launch { deepSeekDrawerState.open() } },
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    hazeState = hazeState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            DeepSeekDrawerLayout(
                drawerState = deepSeekDrawerState,
                drawerWidth = 300.dp,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        hazeState = hazeState,
                    )
                },
                content = {
                    ChatPageContent(
                        onStartVoiceMode = startVoiceMode,
                        inputState = inputState,
                        loadingJob = loadingJob,
                        processingStatus = mergedStatus,
                        setting = setting,
                        conversation = conversation,
                        drawerState = null,
                        deepSeekDrawerState = deepSeekDrawerState,
                        onOpenDrawer = { scope.launch { deepSeekDrawerState.open() } },
                        navController = navController,
                        vm = vm,
                        chatListState = chatListState,
                        hazeState = hazeState,
                        enableWebSearch = enableWebSearch,
                        currentChatModel = currentChatModel,
                        bigScreen = false,
                        errors = errors,
                        onDismissError = { vm.dismissError(it) },
                        onClearAllErrors = { vm.clearAllErrors() },
                    )
                }
            )
        }
    }
}

@Composable
private fun subAgentStatusLine(runs: Map<String, SubAgentRun>): String? {
    val active = runs.values.filter {
        it.status == SubAgentStatus.RUNNING || it.status == SubAgentStatus.PENDING
    }
    if (active.isEmpty()) return null
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active.size) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val oldestStartMs = active.minOf { it.startedAtMs }
    val elapsedSeconds = ((nowMs - oldestStartMs) / 1_000L).coerceAtLeast(0L)
    return stringResource(
        R.string.chat_page_subagents_running,
        active.size,
        formatElapsed(elapsedSeconds),
    )
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(minutes, remaining)
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState? = null,
    deepSeekDrawerState: DeepSeekDrawerState? = null,
    onOpenDrawer: (() -> Unit)? = null,
    hazeState: HazeState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val turnFooters by vm.turnFooters.collectAsStateWithLifecycle()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val assistant = setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    val onUpdateSearchMode: (SearchMode) -> Unit = { mode ->
        val current = setting.getCurrentAssistant()
        val model = currentChatModel
        vm.updateSettings(
            setting.copy(
                assistants = setting.assistants.map { assistant ->
                    if (assistant.id == current.id) {
                        assistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
                    } else {
                        assistant
                    }
                },
                providers = if (model == null) {
                    setting.providers
                } else {
                    setting.providers.map { provider ->
                        provider.editModel(
                            model.copy(
                                tools = if (mode == SearchMode.BUILT_IN) {
                                    model.tools + BuiltInTools.Search
                                } else {
                                    model.tools - BuiltInTools.Search
                                }
                            )
                        )
                    }
                },
            )
        )
    }
    val onUpdateChatModel: (Model) -> Unit = { model ->
        vm.setConversationChatModel(model)
    }
    val onUpdateSearchService: (Int) -> Unit = { index ->
        vm.updateSettings(setting.copy(searchServiceSelected = index))
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(
            setting = setting,
            modifier = Modifier.hazeSource(hazeState, key = CHAT_WALLPAPER_HAZE_KEY),
        )
        Scaffold(
            topBar = {},
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                ChatInput(
                    onStartVoiceMode = onStartVoiceMode,
                    voiceState = voiceState,
                    onStopVoiceMode = vm.voiceSession::stop,
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    loading = loadingJob != null,
                    settings = setting,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    onSendClick = {
                        if (setting.init) return@ChatInput
                        if (currentChatModel == null) {
                            toaster.show(
                                context.getString(R.string.chat_select_model_first),
                                type = ToastType.Error,
                            )
                            navController.navigate(Screen.GettingStarted)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(inputState.getContents())
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                }
                            )
                        )
                    },
                    enableSearch = enableWebSearch,
                    conversationChatModelId = conversation.chatModelId,
                    onUpdateChatModel = onUpdateChatModel,
                    onUpdateModelReasoningLevel = { model, level ->
                        vm.setModelReasoningLevel(model, level)
                    },
                    onUpdateSearchMode = onUpdateSearchMode,
                    onUpdateSearchService = onUpdateSearchService,
                    onMoreClick = {
                        showFilesSheet = true
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            var topBarHeightPx by remember { mutableStateOf(0) }
            val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
            val layoutDirection = LocalLayoutDirection.current
            val listInset = PaddingValues(
                start = innerPadding.calculateLeftPadding(layoutDirection),
                top = if (topBarHeightPx > 0) {
                    topBarHeight
                } else {
                    innerPadding.calculateTopPadding() + 64.dp
                },
                end = innerPadding.calculateRightPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                ChatList(
                    innerPadding = listInset,
                    conversation = conversation,
                    turnFooters = turnFooters,
                    state = chatListState,
                    loading = loadingJob != null,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    settings = setting,
                    hazeState = hazeState,
                    errors = errors,
                    onDismissError = onDismissError,
                    onClearAllErrors = onClearAllErrors,
                    onRegenerate = {
                        vm.regenerateAtMessage(it)
                    },
                    onEdit = {
                        inputState.editingMessage = it.id
                        inputState.setContents(it.parts)
                    },
                    onForkMessage = {
                        scope.launch {
                            val fork = vm.forkMessage(message = it)
                            navigateToChatPage(navController, chatId = fork.id)
                        }
                    },
                    onDelete = {
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(it)
                        }
                    },
                    onUpdateMessage = { newNode ->
                        vm.updateConversation(
                            conversation.copy(
                                messageNodes = conversation.messageNodes.map { node ->
                                    if (node.id == newNode.id) {
                                        newNode
                                    } else {
                                        node
                                    }
                                }
                            ))
                        vm.saveConversationAsync()
                    },
                    onClickSuggestion = { suggestion ->
                        inputState.editingMessage = null
                        inputState.setMessageText(suggestion)
                    },
                    onTranslate = { message, locale ->
                        vm.translateMessage(message, locale)
                    },
                    onClearTranslation = { message ->
                        vm.clearTranslationField(message.id)
                    },
                    onJumpToMessage = { index ->
                        previewMode = false
                        scope.launch {
                            chatListState.requestScrollToItem(index)
                        }
                    },
                    onToolApproval = { toolCallId, approved, reason, scopeArg, toolName ->
                        vm.handleToolApproval(toolCallId, approved, reason, scopeArg, toolName)
                    },
                    onToolAnswer = { toolCallId, answer ->
                        vm.handleToolAnswer(toolCallId, answer)
                    },
                    onRerunTool = { toolCallId ->
                        vm.rerunTool(toolCallId)
                    },
                    onToggleFavorite = { node ->
                        vm.toggleMessageFavorite(node)
                    },
                    onConversationSystemPromptChange = { newPrompt ->
                        vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                        vm.saveConversationAsync()
                    },
                )
                val darkTheme = LocalDarkMode.current
                val photoShown = chatPhotoShown(
                    effectiveBackground(
                        darkTheme = darkTheme,
                        background = assistant.background,
                        darkBackground = assistant.darkBackground,
                        lightBackground = assistant.lightBackground,
                        mode = assistant.backgroundMode,
                    ),
                    assistant.useGradientBackground,
                )
                val topFade = titleBarFade(
                    darkTheme = darkTheme,
                    photoShown = photoShown,
                    cleanLayout = setting.displaySetting.cleanLayout,
                )
                val bottomFade = bottomBarFade(darkTheme = darkTheme, photoShown = photoShown)
                val fadeColor = MaterialTheme.colorScheme.background

                val topFadeExtension = 40.dp
                val topFadeHeight = if (topBarHeightPx > 0) {
                    topBarHeight + topFadeExtension
                } else {
                    innerPadding.calculateTopPadding() + 64.dp + topFadeExtension
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topFadeHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to fadeColor.copy(alpha = topFade.topAlpha),
                                (if (topBarHeightPx > 0) (topBarHeight / topFadeHeight).coerceIn(0.3f, 0.7f) else 0.5f) to
                                    fadeColor.copy(alpha = topFade.midAlpha),
                                1f to Color.Transparent,
                            )
                        )
                )

                val bottomBarHeight = innerPadding.calculateBottomPadding()
                val bottomFadeExtension = 48.dp
                val bottomFadeHeight = bottomBarHeight + bottomFadeExtension
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomFadeHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                (bottomFadeExtension / bottomFadeHeight).coerceIn(0.2f, 0.6f) to
                                    fadeColor.copy(alpha = bottomFade.midAlpha),
                                1f to fadeColor.copy(alpha = bottomFade.bottomAlpha),
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topBarHeightPx = it.height },
                ) {
                    if (setting.displaySetting.cleanLayout) {
                        CleanTopBar(
                            settings = setting,
                            conversation = conversation,
                            turnFooters = turnFooters,
                            drawerState = drawerState ?: rememberDrawerState(initialValue = DrawerValue.Closed),
                            bigScreen = bigScreen,
                            previewMode = previewMode,
                            onClickMenu = { previewMode = !previewMode },
                            onNewChat = { navigateToChatPage(navController) },
                        )
                    } else TopBarWithDeepSeek(
                        settings = setting,
                        conversation = conversation,
                        turnFooters = turnFooters,
                        bigScreen = bigScreen,
                        drawerState = drawerState ?: rememberDrawerState(initialValue = DrawerValue.Closed),
                        deepSeekDrawerState = deepSeekDrawerState,
                        hazeState = hazeState,
                        previewMode = previewMode,
                        onNewChat = {
                            navigateToChatPage(navController)
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                    )
                }
            }
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = {
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { assistant ->
                            if (assistant.id == it.id) {
                                it
                            } else {
                                assistant
                            }
                        }
                    )
                )
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    turnFooters: Map<String, TurnFooter>,
    drawerState: DrawerState,
    hazeState: HazeState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val activity = LocalContext.current as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                GlassTopBarButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    },
                    hazeState = hazeState,
                ) {
                    Icon(HugeIcons.Menu03, stringResource(R.string.menu))
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            val assistant = settings.getCurrentAssistant()
            val titleOnPhoto = looseTextNeedsPhotoPlate(
                darkTheme = LocalDarkMode.current,
                photoShown = chatPhotoShown(
                    effectiveBackground(
                        darkTheme = LocalDarkMode.current,
                        background = assistant.background,
                        darkBackground = assistant.darkBackground,
                        lightBackground = assistant.lightBackground,
                        mode = assistant.backgroundMode,
                    ),
                    assistant.useGradientBackground,
                ),
            )
            val titleFrost = frostedPlateSpec(titleOnPhoto, Build.VERSION.SDK_INT)
            val titleShape = if (titleFrost != null) RoundedCornerShape(percent = 50) else RoundedCornerShape(12.dp)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        drawerVm.requestRenameConversation(conversation)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                modifier = if (titleFrost != null) {
                    Modifier.frostedPlate(hazeState, titleShape, titleFrost)
                } else {
                    Modifier
                },
                shape = titleShape,
                color = if (titleOnPhoto && titleFrost == null) {
                    MaterialTheme.colorScheme.surface.copy(alpha = PHOTO_TITLE_PLATE_ALPHA)
                } else {
                    Color.Transparent
                },
            ) {
                Column(
                    modifier = when {
                        titleFrost != null -> Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        titleOnPhoto -> Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        else -> Modifier
                    },
                ) {
                    val model = settings.resolveChatModel(conversation.chatModelId, conversation.assistantId)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null) {
                        val usedTokens = remember(turnFooters, conversation) {
                            conversationUsedTokens(turnFooters, conversation)
                        }
                        Text(
                            text = topBarSubtitle(
                                assistantName = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                                modelName = model.displayName,
                                usedTokens = usedTokens,
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                }
            }
        },
        actions = {
            GlassTopBarButton(
                onClick = {
                    onClickMenu()
                },
                hazeState = hazeState,
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, stringResource(R.string.accessibility_chat_options))
            }

            GlassTopBarButton(
                onClick = {
                    onNewChat()
                },
                hazeState = hazeState,
            ) {
                Icon(HugeIcons.MessageAdd01, stringResource(R.string.chat_page_new_message))
            }
        },
    )
}

@Composable
private fun TopBarWithDeepSeek(
    settings: Settings,
    conversation: Conversation,
    turnFooters: Map<String, TurnFooter>,
    bigScreen: Boolean,
    drawerState: DrawerState,
    deepSeekDrawerState: DeepSeekDrawerState?,
    hazeState: HazeState,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val activity = LocalContext.current as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                GlassTopBarButton(
                    onClick = {
                        if (deepSeekDrawerState != null) {
                            scope.launch { deepSeekDrawerState.open() }
                        } else {
                            scope.launch { drawerState.open() }
                        }
                    },
                    hazeState = hazeState,
                ) {
                    Icon(HugeIcons.Menu03, stringResource(R.string.menu))
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            val assistant = settings.getCurrentAssistant()
            val titleOnPhoto = looseTextNeedsPhotoPlate(
                darkTheme = LocalDarkMode.current,
                photoShown = chatPhotoShown(
                    effectiveBackground(
                        darkTheme = LocalDarkMode.current,
                        background = assistant.background,
                        darkBackground = assistant.darkBackground,
                        lightBackground = assistant.lightBackground,
                        mode = assistant.backgroundMode,
                    ),
                    assistant.useGradientBackground,
                ),
            )
            val titleFrost = frostedPlateSpec(titleOnPhoto, Build.VERSION.SDK_INT)
            val titleShape = if (titleFrost != null) RoundedCornerShape(percent = 50) else RoundedCornerShape(12.dp)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        drawerVm.requestRenameConversation(conversation)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                modifier = if (titleFrost != null) {
                    Modifier.frostedPlate(hazeState, titleShape, titleFrost)
                } else {
                    Modifier
                },
                shape = titleShape,
                color = if (titleOnPhoto && titleFrost == null) {
                    MaterialTheme.colorScheme.surface.copy(alpha = PHOTO_TITLE_PLATE_ALPHA)
                } else {
                    Color.Transparent
                },
            ) {
                Column(
                    modifier = when {
                        titleFrost != null -> Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        titleOnPhoto -> Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        else -> Modifier
                    },
                ) {
                    val model = settings.resolveChatModel(conversation.chatModelId, conversation.assistantId)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null) {
                        val usedTokens = remember(turnFooters, conversation) {
                            conversationUsedTokens(turnFooters, conversation)
                        }
                        Text(
                            text = topBarSubtitle(
                                assistantName = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                                modelName = model.displayName,
                                usedTokens = usedTokens,
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                }
            }
        },
        actions = {
            GlassTopBarButton(
                onClick = {
                    onClickMenu()
                },
                hazeState = hazeState,
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, stringResource(R.string.accessibility_chat_options))
            }

            GlassTopBarButton(
                onClick = {
                    onNewChat()
                },
                hazeState = hazeState,
            ) {
                Icon(HugeIcons.MessageAdd01, stringResource(R.string.chat_page_new_message))
            }
        },
    )
}

@Composable
private fun GlassTopBarButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    content: @Composable () -> Unit,
) {
    val glassStyle = rikkaGlassStyle(CircleShape, blurRadius = 12.dp, depth = 0.4f)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = glassStyle,
            ),
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
