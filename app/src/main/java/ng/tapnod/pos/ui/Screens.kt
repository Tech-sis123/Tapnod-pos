package ng.tapnod.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ng.tapnod.pos.NfcStatus
import ng.tapnod.pos.Outcome
import ng.tapnod.pos.PosViewModel
import ng.tapnod.pos.Sale
import ng.tapnod.pos.Screen
import ng.tapnod.pos.TapMode
import ng.tapnod.pos.UiState
import ng.tapnod.pos.data.CardInfo
import ng.tapnod.pos.data.Customer
import ng.tapnod.pos.data.SimOutcome
import ng.tapnod.pos.util.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ================================================================== */
/* Root                                                                */
/* ================================================================== */

@Composable
fun TapnodApp(
    state: UiState,
    vm: PosViewModel,
    onOpenNfcSettings: () -> Unit,
    onOpenTtsSettings: () -> Unit
) {
    BackHandler(enabled = state.screen !is Screen.Amount) {
        when (state.screen) {
            is Screen.Waiting -> vm.cancelWaiting()
            is Screen.Result, is Screen.LinkDone -> vm.newSale()
            is Screen.Receipt -> vm.backToResult()
            is Screen.Settings -> vm.closeSettings()
            is Screen.Reading -> Unit
            else -> vm.cancelTap()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TP.Ground)
            .safeDrawingPadding()
            .padding(10.dp)
    ) {
        AnimatedContent(
            targetState = state.screen,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "screen"
        ) { screen ->
            when (screen) {
                is Screen.Amount -> AmountScreen(state, vm)
                is Screen.TapCard -> TapScreen(state, vm, screen.mode, onOpenNfcSettings)
                is Screen.CameraScan -> CameraScanScreen(state, vm, screen.mode)
                is Screen.Reading -> ReadingScreen(state, screen.mode)
                is Screen.Waiting -> WaitingScreen(state, vm, screen.sale)
                is Screen.Result -> ResultScreen(vm, screen.outcome, screen.sale)
                is Screen.Receipt -> ReceiptScreen(state, vm, screen.outcome, screen.sale)
                is Screen.NotLinked -> NotLinkedScreen(vm, screen.card)
                is Screen.LinkPick -> LinkPickScreen(state, vm)
                is Screen.LinkDone -> LinkDoneScreen(vm, screen.customer, screen.card)
                is Screen.Failure -> FailureScreen(vm, screen.title, screen.message, screen.retry)
                is Screen.Settings -> SettingsScreen(state, vm, onOpenTtsSettings)
            }
        }
    }
}

/* ================================================================== */
/* A1: amount entry                                                    */
/* ================================================================== */

@Composable
private fun AmountScreen(state: UiState, vm: PosViewModel) {
    val s = state.settings
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {

        Row(
            Modifier
                .fillMaxWidth()
                .height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(), color = TP.Ink
            ) {
                BrandMark(Modifier.align(Alignment.TopStart))
                Eyebrow("Point of sale", Modifier.align(Alignment.BottomStart), color = TP.InkMute)
            }
            Tile(
                Modifier
                    .weight(1.8f)
                    .fillMaxHeight()
            ) {
                Eyebrow("Merchant", Modifier.align(Alignment.TopStart))
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text(
                        s.merchantName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusLine(state)
                }
            }
        }

        val amountText = Money.format(state.amountNaira)
        val error = state.amountError
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            color = if (error) TP.Red else TP.Paper
        ) {
            Eyebrow(
                if (error) "Enter an amount first" else "Step 1 of 3 · Amount",
                Modifier.align(Alignment.TopStart),
                color = if (error) TP.White else TP.Mute
            )
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
                Text(
                    amountText,
                    fontSize = amountSize(amountText),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp,
                    color = if (error) TP.White else if (state.amountNaira == 0L) TP.Line else TP.Ink,
                    maxLines = 1
                )
                Text(
                    if (state.amountNaira > 0) Money.words(state.amountNaira)
                    else "Type the amount the customer asked for",
                    fontSize = 13.sp,
                    color = if (error) TP.White else TP.Mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Keypad(
            Modifier
                .fillMaxWidth()
                .weight(1.6f), vm
        )

        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
                color = TP.Soft,
                onClick = { vm.openSettings() },
                onClickLabel = "Open settings"
            ) {
                Eyebrow("Setup", Modifier.align(Alignment.TopStart))
                Text(
                    "Settings", Modifier.align(Alignment.BottomStart),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            Tile(
                Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                onClick = { vm.startLink() },
                onClickLabel = "Link a card to a customer"
            ) {
                Eyebrow("Setup", Modifier.align(Alignment.TopStart))
                Text(
                    "Link card", Modifier.align(Alignment.BottomStart),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            Tile(
                Modifier
                    .weight(1.9f)
                    .fillMaxHeight(),
                color = TP.Lime,
                onClick = { vm.charge() },
                onClickLabel = "Charge the customer"
            ) {
                Eyebrow("Next", Modifier.align(Alignment.TopStart), color = TP.Ink)
                Text(
                    "Charge →", Modifier.align(Alignment.BottomStart),
                    fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp
                )
            }
        }
    }
}

private fun amountSize(text: String): TextUnit = when {
    text.length <= 7 -> 64.sp
    text.length <= 9 -> 54.sp
    text.length <= 11 -> 44.sp
    else -> 38.sp
}

@Composable
private fun StatusLine(state: UiState) {
    val (dot, label) = when {
        state.nfc == NfcStatus.MISSING -> TP.Red to "No NFC on this phone"
        state.nfc == NfcStatus.OFF -> TP.Red to "NFC is off"
        state.settings.simulator -> TP.Lime to "Simulator · offline demo"
        state.online == true -> Color(0xFF1DB954) to "Live · connected"
        state.online == false -> TP.Red to "Offline · check backend"
        else -> TP.InkMute to "Connecting…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${label.uppercase()} · ${state.settings.terminalId}",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = TP.Mute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Keypad(modifier: Modifier, vm: PosViewModel) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "DEL")
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GAP)) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GAP)
            ) {
                row.forEach { key ->
                    val isDel = key == "DEL"
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(TileShape)
                            .background(if (isDel) TP.Line else TP.Paper)
                            .semantics {
                                contentDescription = if (isDel) "Delete. Long press to clear." else key
                                role = Role.Button
                            }
                            .pointerInput(key) {
                                detectTapGestures(
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isDel) vm.backspace() else vm.press(key)
                                    },
                                    onLongPress = {
                                        if (isDel) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            vm.clearAmount()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isDel) "Del" else key,
                            fontSize = if (isDel) 18.sp else 30.sp,
                            fontWeight = if (isDel) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
            }
        }
    }
}

/* ================================================================== */
/* A2: tap card                                                        */
/* ================================================================== */

@Composable
private fun TapScreen(state: UiState, vm: PosViewModel, mode: TapMode, onOpenNfcSettings: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Ink, padding = 22.dp
        ) {
            Eyebrow(
                if (mode == TapMode.CHARGE) "Step 2 of 3 · Card" else "Setup · Link a card",
                Modifier.align(Alignment.TopStart),
                color = TP.Lime
            )
            if (state.nfc == NfcStatus.READY) {
                PulseCard(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                )
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    when (state.nfc) {
                        NfcStatus.READY -> "Tap card"
                        NfcStatus.OFF -> "Turn on NFC"
                        NfcStatus.MISSING -> "No NFC"
                    },
                    color = TP.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2.5).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (state.nfc) {
                        NfcStatus.READY -> "Hold the card flat on the back of this phone until it beeps. Not working? Scan with camera."
                        NfcStatus.OFF -> "NFC is switched off on this phone. Turn it on to read cards."
                        NfcStatus.MISSING -> "This phone has no NFC. Tap Scan with camera to read the card instead."
                    },
                    color = TP.InkMute,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = TP.Lime,
                onClick = { vm.openCamera(mode) },
                onClickLabel = "Scan the card with the camera",
                padding = 16.dp
            ) {
                Text("Scan with camera", Modifier.align(Alignment.CenterStart), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (mode == TapMode.CHARGE) {
                Tile(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { vm.chooseCustomerToCharge() },
                    onClickLabel = "Choose the customer instead of reading a card",
                    padding = 16.dp
                ) {
                    Text("Choose customer", Modifier.align(Alignment.CenterStart), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            if (mode == TapMode.CHARGE) {
                Tile(
                    Modifier
                        .weight(2f)
                        .fillMaxHeight(), color = TP.Lime
                ) {
                    Eyebrow("Amount", Modifier.align(Alignment.TopStart), color = TP.Ink)
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text(
                            Money.format(state.amountNaira),
                            fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp,
                            maxLines = 1
                        )
                        Text(
                            state.settings.merchantName,
                            fontSize = 13.sp, color = TP.Ink.copy(alpha = 0.7f), maxLines = 1
                        )
                    }
                }
            } else {
                Tile(
                    Modifier
                        .weight(2f)
                        .fillMaxHeight()
                ) {
                    Eyebrow("Linking to", Modifier.align(Alignment.TopStart))
                    Text(
                        state.linkCustomer?.name ?: "Customer",
                        Modifier.align(Alignment.BottomStart),
                        fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
                        maxLines = 2
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(GAP)
            ) {
                when {
                    state.nfc == NfcStatus.OFF -> SmallAction(
                        "Open NFC settings", TP.Lime,
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { onOpenNfcSettings() }

                    state.settings.simulator -> SmallAction(
                        "Simulate tap", TP.Soft,
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { vm.simulateTap() }
                }
                SmallAction(
                    "Cancel", TP.Line,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { vm.cancelTap() }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier, color = color, onClick = onClick, padding = 14.dp) {
        Text(
            label, Modifier.align(Alignment.BottomStart),
            fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp
        )
    }
}

@Composable
private fun PulseCard(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rings")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    Canvas(modifier) {
        val c = center
        val maxR = size.minDimension / 2f
        for (i in 0 until 3) {
            val f = (p + i / 3f) % 1f
            drawCircle(
                color = TP.Lime.copy(alpha = (1f - f) * 0.6f),
                radius = maxR * (0.3f + 0.7f * f),
                center = c,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        val w = maxR * 0.95f
        val h = w * 0.63f
        val left = c.x - w / 2f
        val topY = c.y - h / 2f
        drawRoundRect(
            color = TP.Lime,
            topLeft = Offset(left, topY),
            size = Size(w, h),
            cornerRadius = CornerRadius(12.dp.toPx())
        )
        drawRoundRect(
            color = TP.Ink.copy(alpha = 0.85f),
            topLeft = Offset(left + w * 0.12f, c.y - h * 0.14f),
            size = Size(w * 0.18f, h * 0.28f),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
        val arcCx = left + w * 0.62f
        for (k in 1..3) {
            val r = k * 8.dp.toPx()
            drawArc(
                color = TP.Ink,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(arcCx - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/* ================================================================== */
/* Reading                                                             */
/* ================================================================== */

@Composable
private fun ReadingScreen(state: UiState, mode: TapMode) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Ink, padding = 22.dp
        ) {
            Eyebrow(
                if (mode == TapMode.CHARGE) "Step 2 of 3 · Card" else "Setup · Link a card",
                Modifier.align(Alignment.TopStart), color = TP.Lime
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp),
                color = TP.Lime,
                trackColor = TP.InkRaised,
                strokeWidth = 6.dp
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    if (mode == TapMode.CHARGE) "Sending to\nher phone" else "Linking card",
                    color = TP.White, fontSize = 41.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp, lineHeight = 50.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Card read. Tapnod is sealing the request.",
                    color = TP.InkMute, fontSize = 15.sp
                )
            }
        }
        if (mode == TapMode.CHARGE) {
            Tile(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp), color = TP.Lime
            ) {
                Eyebrow("Amount", Modifier.align(Alignment.TopStart), color = TP.Ink)
                Text(
                    Money.format(state.amountNaira), Modifier.align(Alignment.BottomStart),
                    fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp
                )
            }
        }
    }
}

/* ================================================================== */
/* A5: waiting for the customer                                        */
/* ================================================================== */

@Composable
private fun WaitingScreen(state: UiState, vm: PosViewModel, sale: Sale) {
    val total = ((sale.expiresAtMs - sale.startedAtMs) / 1000f).coerceAtLeast(1f)
    val frac = (state.secondsLeft / total).coerceIn(0f, 1f)
    val clock = "%d:%02d".format(state.secondsLeft / 60, state.secondsLeft % 60)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Ink, padding = 22.dp
        ) {
            Eyebrow("Step 3 of 3 · Waiting for customer", Modifier.align(Alignment.TopStart), color = TP.Lime)
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(
                    clock, color = TP.White, fontSize = 82.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-4).sp
                )
                Text(
                    "Her phone is saying the amount out loud.",
                    color = TP.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Only her fingerprint can approve it.",
                    color = TP.InkMute, fontSize = 15.sp
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TP.InkRaised)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(TP.Lime)
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(116.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(1.2f)
                    .fillMaxHeight(), color = TP.Lime
            ) {
                Eyebrow("Amount", Modifier.align(Alignment.TopStart), color = TP.Ink)
                Text(
                    Money.format(sale.amountNaira), Modifier.align(Alignment.BottomStart),
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp, maxLines = 1
                )
            }
            CardTile(
                sale.card,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(2f)
                    .fillMaxHeight()
            ) {
                Eyebrow("Customer", Modifier.align(Alignment.TopStart))
                Text(
                    sale.customerName ?: "Linked customer", Modifier.align(Alignment.BottomStart),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            SmallAction(
                "Cancel", TP.Line,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { vm.cancelWaiting() }
        }
    }
}

@Composable
private fun CardTile(card: CardInfo, modifier: Modifier) {
    Tile(modifier) {
        Eyebrow("Card", Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                if (card.last4.isBlank()) "No card" else "•••• ${card.last4}",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, maxLines = 1
            )
            Text(card.scheme, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = TP.Mute)
        }
    }
}

/* ================================================================== */
/* A6: approved / declined / timed out                                 */
/* ================================================================== */

@Composable
private fun ResultScreen(vm: PosViewModel, outcome: Outcome, sale: Sale) {
    val bg: Color
    val fg: Color
    val title: String
    val line: String
    when (outcome) {
        Outcome.APPROVED -> {
            bg = TP.Lime; fg = TP.Ink; title = "Approved"
            line = "${sale.customerName ?: "The customer"} approved it with her fingerprint."
        }
        Outcome.DECLINED -> {
            bg = TP.Red; fg = TP.White; title = "Declined"
            line = "Declined by customer. Not one naira moved."
        }
        Outcome.EXPIRED -> {
            bg = TP.Line; fg = TP.Ink; title = "Timed out"
            line = "No answer in 2 minutes. Nothing moved."
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = bg, padding = 22.dp
        ) {
            Eyebrow("Payment result", Modifier.align(Alignment.TopStart), color = fg.copy(alpha = 0.7f))
            ResultGlyph(
                outcome, fg,
                Modifier
                    .align(Alignment.TopEnd)
                    .size(72.dp)
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    title, color = fg, fontSize = 61.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-3.5).sp, lineHeight = 72.sp
                )
                Text(
                    Money.format(sale.amountNaira), color = fg, fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp
                )
                Spacer(Modifier.height(10.dp))
                Text(line, color = fg.copy(alpha = 0.8f), fontSize = 16.sp, lineHeight = 21.sp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Eyebrow("Reference", Modifier.align(Alignment.TopStart))
                Text(
                    sale.reference ?: "—", Modifier.align(Alignment.BottomStart),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            CardTile(
                sale.card,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                color = TP.Ink,
                onClick = { vm.newSale() },
                onClickLabel = "Start a new sale"
            ) {
                Eyebrow("Next", Modifier.align(Alignment.TopStart), color = TP.Lime)
                Text(
                    "New sale →", Modifier.align(Alignment.BottomStart),
                    color = TP.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
                )
            }
            Tile(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = { vm.showReceipt() },
                onClickLabel = "View receipt"
            ) {
                Eyebrow("View", Modifier.align(Alignment.TopStart))
                Text(
                    "Receipt", Modifier.align(Alignment.BottomStart),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ResultGlyph(outcome: Outcome, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color = color, radius = s / 2f - stroke.width / 2f, style = Stroke(width = s * 0.05f))
        when (outcome) {
            Outcome.APPROVED -> {
                val path = Path().apply {
                    moveTo(s * 0.28f, s * 0.52f)
                    lineTo(s * 0.44f, s * 0.68f)
                    lineTo(s * 0.73f, s * 0.36f)
                }
                drawPath(path, color, style = stroke)
            }
            Outcome.DECLINED -> {
                drawLine(color, Offset(s * 0.34f, s * 0.34f), Offset(s * 0.66f, s * 0.66f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.34f), Offset(s * 0.34f, s * 0.66f), stroke.width, StrokeCap.Round)
            }
            Outcome.EXPIRED -> {
                drawLine(color, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.5f, s * 0.28f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.66f, s * 0.6f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

/* ================================================================== */
/* A9: receipt                                                         */
/* ================================================================== */

@Composable
private fun ReceiptScreen(state: UiState, vm: PosViewModel, outcome: Outcome, sale: Sale) {
    val fmt = SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.UK)
    val whenText = fmt.format(Date(sale.finishedAtMs ?: sale.startedAtMs))
    val statusText = when (outcome) {
        Outcome.APPROVED -> "Approved"
        Outcome.DECLINED -> "Declined by customer"
        Outcome.EXPIRED -> "Timed out"
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), padding = 22.dp
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(dark = false)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                when (outcome) {
                                    Outcome.APPROVED -> TP.Lime
                                    Outcome.DECLINED -> TP.Red
                                    Outcome.EXPIRED -> TP.Line
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            statusText.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = if (outcome == Outcome.DECLINED) TP.White else TP.Ink
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("Receipt", fontSize = 37.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
                Text(whenText, fontSize = 14.sp, color = TP.Mute)
                Spacer(Modifier.height(20.dp))
                ReceiptRow("Merchant", state.settings.merchantName)
                ReceiptRow("Merchant ID", state.settings.merchantId)
                ReceiptRow("Terminal", state.settings.terminalId)
                ReceiptRow("Amount", Money.format(sale.amountNaira), big = true)
                ReceiptRow("In words", Money.words(sale.amountNaira))
                ReceiptRow("Card", if (sale.card.last4.isBlank()) "Picked on POS · Tapnod ID" else "•••• ${sale.card.last4} · ${sale.card.scheme}")
                ReceiptRow("Customer", sale.customerName ?: "—")
                ReceiptRow("Status", statusText)
                ReceiptRow("Reference", sale.reference ?: "—")
                ReceiptRow("Request", sale.requestId)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Approved on the customer's own phone with her fingerprint. " +
                        "Tapnod never stores or sees the card number.",
                    fontSize = 12.sp, color = TP.Mute, lineHeight = 16.sp
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            SmallAction(
                "Back", TP.Line,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { vm.backToResult() }
            Tile(
                Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                color = TP.Ink,
                onClick = { vm.newSale() },
                onClickLabel = "Start a new sale"
            ) {
                Text(
                    "New sale →", Modifier.align(Alignment.BottomStart),
                    color = TP.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, big: Boolean = false) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TP.Line)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(), Modifier.width(110.dp),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = TP.Mute
            )
            Text(
                value,
                fontSize = if (big) 22.sp else 15.sp,
                fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Medium,
                letterSpacing = if (big) (-0.8).sp else 0.sp
            )
        }
    }
}

/* ================================================================== */
/* A7: card not linked                                                 */
/* ================================================================== */

@Composable
private fun NotLinkedScreen(vm: PosViewModel, card: CardInfo) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Ink, padding = 22.dp
        ) {
            Eyebrow("Unknown card", Modifier.align(Alignment.TopStart), color = TP.Lime)
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    "Card not linked to Tapnod", color = TP.White, fontSize = 39.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, lineHeight = 48.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "•••• ${card.last4} · ${card.scheme}",
                    color = TP.Lime, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This card isn't connected to a Tapnod customer yet. Link it once, then charge again.",
                    color = TP.InkMute, fontSize = 15.sp, lineHeight = 20.sp
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                color = TP.Lime,
                onClick = { vm.startLink(card) },
                onClickLabel = "Link this card"
            ) {
                Eyebrow("Setup", Modifier.align(Alignment.TopStart), color = TP.Ink)
                Text(
                    "Link this card →", Modifier.align(Alignment.BottomStart),
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
                )
            }
            SmallAction(
                "Back", TP.Line,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { vm.cancelTap() }
        }
    }
}

/* ================================================================== */
/* A8: link a card to a demo customer                                  */
/* ================================================================== */

@Composable
private fun LinkPickScreen(state: UiState, vm: PosViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .height(168.dp), color = TP.Ink, padding = 22.dp
        ) {
            Eyebrow(
                if (state.pickForCharge) "Step 2 of 3 · Customer" else "Setup · Link a card",
                Modifier.align(Alignment.TopStart), color = TP.Lime
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    if (state.pickForCharge) "Who is paying?" else "Who owns the card?",
                    color = TP.White, fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp
                )
                val pending = state.pendingLinkCard
                Text(
                    when {
                        state.pickForCharge -> "Pick the customer. Their phone gets the payment to approve."
                        pending != null -> "Card •••• ${pending.last4} is ready to link."
                        else -> "Pick a customer, then tap their card."
                    },
                    color = TP.InkMute, fontSize = 15.sp
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GAP)
        ) {
            when {
                state.customersLoading -> Tile(
                    Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    Text("Loading customers…", Modifier.align(Alignment.CenterStart), fontSize = 17.sp, color = TP.Mute)
                }

                state.customersError != null -> Tile(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    color = TP.Red,
                    onClick = { vm.loadCustomers() },
                    onClickLabel = "Try loading customers again"
                ) {
                    Eyebrow("Couldn't load", Modifier.align(Alignment.TopStart), color = TP.White)
                    Text(
                        "${state.customersError}\nTap to try again.", Modifier.align(Alignment.BottomStart),
                        color = TP.White, fontSize = 15.sp, lineHeight = 20.sp
                    )
                }

                else -> state.customers.forEach { c -> CustomerTile(c) { vm.pickLinkCustomer(c) } }
            }
        }
        SmallAction(
            "Cancel", TP.Line,
            Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) { vm.cancelTap() }
    }
}

@Composable
private fun CustomerTile(c: Customer, onClick: () -> Unit) {
    Tile(
        Modifier
            .fillMaxWidth()
            .height(96.dp),
        onClick = onClick,
        onClickLabel = "Link to ${c.name}"
    ) {
        Eyebrow(c.note.ifBlank { "Customer" }, Modifier.align(Alignment.TopStart))
        Text(
            c.name, Modifier.align(Alignment.BottomStart),
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
        )
        Text(
            "→", Modifier.align(Alignment.CenterEnd),
            fontSize = 28.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LinkDoneScreen(vm: PosViewModel, customer: Customer, card: CardInfo) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Lime, padding = 22.dp
        ) {
            Eyebrow("Setup complete", Modifier.align(Alignment.TopStart), color = TP.Ink)
            ResultGlyph(
                Outcome.APPROVED, TP.Ink,
                Modifier
                    .align(Alignment.TopEnd)
                    .size(72.dp)
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    "Card linked", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-3).sp, lineHeight = 64.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "•••• ${card.last4} now belongs to ${customer.name}. " +
                        "Charge it and her phone will speak.",
                    fontSize = 16.sp, lineHeight = 21.sp, color = TP.Ink.copy(alpha = 0.8f)
                )
            }
        }
        Tile(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            color = TP.Ink,
            onClick = { vm.newSale() },
            onClickLabel = "Start a sale"
        ) {
            Eyebrow("Next", Modifier.align(Alignment.TopStart), color = TP.Lime)
            Text(
                "Start a sale →", Modifier.align(Alignment.BottomStart),
                color = TP.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
            )
        }
    }
}

/* ================================================================== */
/* Errors                                                              */
/* ================================================================== */

@Composable
private fun FailureScreen(vm: PosViewModel, title: String, message: String, retry: TapMode?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(
            Modifier
                .fillMaxWidth()
                .weight(1f), color = TP.Red, padding = 22.dp
        ) {
            Eyebrow("Problem", Modifier.align(Alignment.TopStart), color = TP.White)
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    title, color = TP.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp, lineHeight = 52.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(message, color = TP.White.copy(alpha = 0.88f), fontSize = 16.sp, lineHeight = 21.sp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            if (retry != null) {
                Tile(
                    Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    color = TP.Ink,
                    onClick = { vm.retry(retry) },
                    onClickLabel = "Try again"
                ) {
                    Text(
                        "Tap again →", Modifier.align(Alignment.BottomStart),
                        color = TP.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
                    )
                }
            }
            SmallAction(
                "Back", TP.Line,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { vm.cancelTap() }
        }
    }
}

/* ================================================================== */
/* Settings                                                            */
/* ================================================================== */

@Composable
private fun SettingsScreen(state: UiState, vm: PosViewModel, onOpenTtsSettings: () -> Unit) {
    var draft by remember(state.settings) { mutableStateOf(state.settings) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Tile(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp), color = TP.Ink, padding = 22.dp
            ) {
                Eyebrow("Tapnod POS · v1.3", Modifier.align(Alignment.TopStart), color = TP.Lime)
                Text(
                    "Settings", Modifier.align(Alignment.BottomStart),
                    color = TP.White, fontSize = 37.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp
                )
            }

            Tile(Modifier.fillMaxWidth()) {
                ToggleRow(
                    "Simulator mode",
                    "Runs offline with demo customers. Turn off to use the live Tapnod backend.",
                    draft.simulator
                ) { draft = draft.copy(simulator = it) }
            }

            Tile(Modifier.fillMaxWidth()) {
                ToggleRow(
                    "Accept any tap",
                    "If a card's number can't be read, the tap still counts as the demo card. Keep on for demo day.",
                    draft.acceptAnyTap
                ) { draft = draft.copy(acceptAnyTap = it) }
            }

            if (draft.simulator) {
                Tile(Modifier.fillMaxWidth()) {
                    Column {
                        Eyebrow("Simulated customer answer")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            SimOutcome.entries.forEach { o ->
                                val selected = draft.simOutcome == o
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) TP.Ink else TP.Soft)
                                        .clickable(role = Role.RadioButton) { draft = draft.copy(simOutcome = o) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        when (o) {
                                            SimOutcome.APPROVE -> "Approve"
                                            SimOutcome.DECLINE -> "Decline"
                                            SimOutcome.NO_ANSWER -> "No answer"
                                        },
                                        color = if (selected) TP.Lime else TP.Ink,
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Tile(Modifier.fillMaxWidth()) {
                Column {
                    Eyebrow("Live backend")
                    Spacer(Modifier.height(10.dp))
                    Field("Backend URL", draft.backendUrl, KeyboardType.Uri, "https://your-tapnod-api.onrender.com") {
                        draft = draft.copy(backendUrl = it)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(TP.Ink)
                                .clickable(role = Role.Button) { vm.testConnection(draft.copy(simulator = false)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Test connection", color = TP.Lime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(state.connectionTest ?: "", fontSize = 13.sp, color = TP.Mute)
                    }
                }
            }

            Tile(Modifier.fillMaxWidth()) {
                Column {
                    Eyebrow("This terminal")
                    Spacer(Modifier.height(10.dp))
                    Field("Merchant name", draft.merchantName) { draft = draft.copy(merchantName = it) }
                    Spacer(Modifier.height(8.dp))
                    Field("Merchant ID", draft.merchantId) { draft = draft.copy(merchantId = it) }
                    Spacer(Modifier.height(8.dp))
                    Field("Terminal ID", draft.terminalId) { draft = draft.copy(terminalId = it) }
                }
            }

            Tile(Modifier.fillMaxWidth()) {
                Column {
                    Eyebrow("Card token secret")
                    Spacer(Modifier.height(10.dp))
                    Field("Secret", draft.tokenSecret) { draft = draft.copy(tokenSecret = it) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Must match the backend. The card number becomes a one-way token on this phone " +
                            "using this secret, so Tapnod never receives the number itself.",
                        fontSize = 12.sp, color = TP.Mute, lineHeight = 16.sp
                    )
                }
            }

            Tile(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow("Speak results", "Say “Payment approved” out loud on this POS.", draft.voice) {
                        draft = draft.copy(voice = it)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TP.Line)
                    )
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("Voice")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.voiceLabel ?: "Loading voices…",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.voiceNigerian) {
                            "Nigerian English voice in use. Tap Next voice to hear the others (${state.voiceCount} installed)."
                        } else {
                            "No Nigerian voice on this phone yet. Tap Install voices, open Google's speech settings, " +
                                "then install English (Nigeria). Come back and tap Next voice."
                        },
                        fontSize = 12.sp, color = TP.Mute, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        Chip("Test", TP.Ink, TP.Lime, Modifier.weight(1f)) { vm.testVoice() }
                        Chip("Next voice", TP.Soft, TP.Ink, Modifier.weight(1.2f)) { vm.nextVoice() }
                        Chip("Install voices", TP.Soft, TP.Ink, Modifier.weight(1.4f)) { onOpenTtsSettings() }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            SmallAction(
                "Cancel", TP.Line,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { vm.closeSettings() }
            Tile(
                Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                color = TP.Lime,
                onClick = {
                    vm.saveSettings(draft)
                    vm.closeSettings()
                },
                onClickLabel = "Save settings"
            ) {
                Text(
                    "Save →", Modifier.align(Alignment.BottomStart),
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 13.sp, color = TP.Mute, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TP.Lime,
                checkedTrackColor = TP.Ink,
                checkedBorderColor = TP.Ink,
                uncheckedThumbColor = TP.White,
                uncheckedTrackColor = TP.Line,
                uncheckedBorderColor = TP.Line
            )
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> @Composable { Text(hint, color = TP.InkMute) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TP.Ink,
            unfocusedBorderColor = TP.Line,
            focusedLabelColor = TP.Ink,
            unfocusedLabelColor = TP.Mute,
            cursorColor = TP.Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
