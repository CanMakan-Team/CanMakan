package sg.edu.nus.iss.canmakan.features.product.verdict

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlag
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascot
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotSize
import sg.edu.nus.iss.canmakan.shared.ui.statusAccentColor
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarOrange
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.InfoBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.InfoBlueContainer
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightAmberBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightPurpleBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.RulePurple
import sg.edu.nus.iss.canmakan.shared.ui.theme.SurfaceMuted
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary
import sg.edu.nus.iss.canmakan.shared.ui.theme.WarningAmber

private enum class DetailTab { FLAGS, ALTERNATIVES }

/* Shows the outcome for one scanned product, with two tabs: the flagged
 * reasons behind the verdict, and safer alternatives to try instead.
 *
 * authors Amelia; Kwok Heng
 */
@Composable
fun ProductDetailScreen(
    product: Product,
    verdict: ScanVerdict, // drives icon/color/label
    flags: List<ProductFlag>,
    alternatives: List<AlternativeProduct>,
    profileName: String,
    scanId: Long?, // backing scans.id row; feedback can't be submitted without it
    feedbackSubmissionState: FeedbackSubmissionState = FeedbackSubmissionState.IDLE,
    onSubmitPositiveFeedback: (scanId: Long) -> Unit = {},
    onSubmitNegativeFeedback: (scanId: Long, comment: String?) -> Unit = { _, _ -> },
    explanation: String? = null, // optional explanation for the verdict
    alternativesError: String? = null,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(DetailTab.FLAGS) }
    val showAlternativesTab = verdict != ScanVerdict.SAFE
    val accent = statusAccentColor(verdict)
    val label = verdictLabel(verdict)
    val mascotPose = mascotPoseFor(verdict)
    val verdictBackdrop = when (verdict) {
        ScanVerdict.SAFE -> LightGreenBackground
        ScanVerdict.WARNING -> LightAmberBackground
        ScanVerdict.UNSAFE -> LightRedBackground
    }
    val scrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomTab.SCAN,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(onClick = onBackClick)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back", color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(verdictBackdrop)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            ) {
                CanMakanMascot(
                    pose = mascotPose,
                    size = CanMakanMascotSize.Banner,
                    contentDescription = "$label verdict",
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = product.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                    )
                    if (product.displayBrand.isNotEmpty()) {
                        Text(
                            text = product.displayBrand,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    if (product.displayBarcode.isNotEmpty()) {
                        Text(
                            text = product.displayBarcode,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceMuted)
            ) {
                DetailTabButton(
                    label = "Details",
                    isSelected = selectedTab == DetailTab.FLAGS,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = DetailTab.FLAGS }
                if (showAlternativesTab) {
                    DetailTabButton(
                        label = "Alternatives",
                        isSelected = selectedTab == DetailTab.ALTERNATIVES,
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = DetailTab.ALTERNATIVES }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    DetailTab.FLAGS -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlagsAndDetailsTab(
                            flags = flags,
                            profileName = profileName,
                            scanId = scanId,
                            feedbackSubmissionState = feedbackSubmissionState,
                            onSubmitPositiveFeedback = onSubmitPositiveFeedback,
                            onSubmitNegativeFeedback = onSubmitNegativeFeedback,
                        )
                    }
                    DetailTab.ALTERNATIVES -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        AlternativesTab(
                            alternatives = alternatives,
                            profileName = profileName,
                            errorMessage = alternativesError
                        )
                    }
                }
            }
        }
    }
}

// Returns the verdict label shown under the mascot.
private fun verdictLabel(verdict: ScanVerdict): String = when (verdict) {
    ScanVerdict.SAFE -> "SAFE"
    ScanVerdict.WARNING -> "WARNING"
    ScanVerdict.UNSAFE -> "UNSAFE"
}

private fun mascotPoseFor(verdict: ScanVerdict): CanMakanMascotPose = when (verdict) {
    ScanVerdict.SAFE -> CanMakanMascotPose.Safe
    ScanVerdict.WARNING -> CanMakanMascotPose.Warning
    ScanVerdict.UNSAFE -> CanMakanMascotPose.Unsafe
}

// Button to switch between tabs in the detail screen
// Tabs: Flags & Details, Alternatives
@Composable
private fun DetailTabButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) CardWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

// Tab 1: Shows the flags and details for the product
@Composable
private fun FlagsAndDetailsTab(
    flags: List<ProductFlag>,
    profileName: String,
    scanId: Long?,
    feedbackSubmissionState: FeedbackSubmissionState,
    onSubmitPositiveFeedback: (scanId: Long) -> Unit,
    onSubmitNegativeFeedback: (scanId: Long, comment: String?) -> Unit,
) {
    Column {

        // Display a message if there are no flags
        if (flags.isEmpty()) {
            Text(
                "No specific flags for this product.",
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Display each flag as a separate box
        flags.forEach { flag ->
            val category = flag.category.orEmpty()
            val isAllergen = category.equals("Allergen", ignoreCase = true)
            val isRule = category.equals("Rule", ignoreCase = true)
            val background = when {
                isAllergen -> LightRedBackground
                isRule -> LightPurpleBackground
                else -> InfoBlueContainer
            }
            val labelColor = when {
                isAllergen -> AvoidRed
                isRule -> RulePurple
                else -> InfoBlue
            }
            val title = category.ifBlank { "Info" }
            val body = flag.label.orEmpty().ifBlank { "Flagged by dietary rules" }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .padding(14.dp)
            ) {
                Text(
                    text = title,
                    color = labelColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Skip duplicate body when title and label are the same raw code.
                if (!body.equals(title, ignoreCase = true)) {
                    Text(
                        text = body,
                        color = labelColor,
                        fontWeight = FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ScanFeedbackRow(
            profileName = profileName,
            scanId = scanId,
            submissionState = feedbackSubmissionState,
            onSubmitPositive = onSubmitPositiveFeedback,
            onSubmitNegative = onSubmitNegativeFeedback,
        )
    }
}

// Row showing which profile the verdict was matched against, plus quick
// thumbs up/down feedback controls for the accuracy of that verdict (UC20).
// Thumbs up logs immediately on tap (fire-and-forget; confetti plays either
// way). Thumbs down opens an optional comment box; only Submit reports it.
// Both need a scanId to actually persist anything.
@Composable
private fun ScanFeedbackRow(
    profileName: String,
    scanId: Long?,
    submissionState: FeedbackSubmissionState,
    onSubmitPositive: (scanId: Long) -> Unit,
    onSubmitNegative: (scanId: Long, comment: String?) -> Unit,
) {
    var feedback by remember { mutableStateOf<ScanFeedback?>(null) }
    // 0 means no confetti burst is active; any other value both marks a burst as
    // active and, being unique per tap, forces the animation to restart on repeat taps.
    var confettiBurstId by remember { mutableStateOf(0) }
    var feedbackComment by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Matched against $profileName's profile",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )

            Box(contentAlignment = Alignment.Center) {
                FeedbackIconButton(
                    outlinedIcon = Icons.Outlined.ThumbUp,
                    filledIcon = Icons.Filled.ThumbUp,
                    contentDescription = "Verdict was helpful",
                    tint = PrimaryGreen,
                    isSelected = feedback == ScanFeedback.THUMBS_UP,
                ) {
                    feedback = ScanFeedback.THUMBS_UP
                    confettiBurstId += 1
                    scanId?.let(onSubmitPositive)
                }
                if (confettiBurstId != 0) {
                    key(confettiBurstId) {
                        // Reports zero size to the Box so the burst doesn't grow the
                        // row while it plays; see SizeExcludedOverlay for why.
                        SizeExcludedOverlay {
                            ConfettiBurst(
                                modifier = Modifier.size(64.dp),
                                onFinished = { confettiBurstId = 0 },
                            )
                        }
                    }
                }
            }

            FeedbackIconButton(
                outlinedIcon = Icons.Outlined.ThumbDown,
                filledIcon = Icons.Filled.ThumbDown,
                contentDescription = "Verdict was inaccurate",
                tint = AvoidRed,
                isSelected = feedback == ScanFeedback.THUMBS_DOWN,
            ) {
                feedback = ScanFeedback.THUMBS_DOWN
            }
        }

        if (feedback == ScanFeedback.THUMBS_DOWN) {
            Spacer(modifier = Modifier.height(10.dp))
            val isSubmitting = submissionState == FeedbackSubmissionState.SUBMITTING
            val isSubmitted = submissionState == FeedbackSubmissionState.SUBMITTED
            val isError = submissionState == FeedbackSubmissionState.ERROR

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = feedbackComment,
                    onValueChange = { feedbackComment = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 84.dp),
                    placeholder = { Text("Tell us what looks wrong with this verdict... (optional)") },
                    minLines = 3,
                    maxLines = 3,
                    enabled = !isSubmitting && !isSubmitted,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                // Success confirmation: dim the textbox behind a translucent white
                // layer and show the confirmation on top, in the app's bold,
                // status-colored style for inline confirmations.
                if (isSubmitted) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardWhite.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Thank you for your feedback!",
                            color = PrimaryGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (isError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sorry, but your feedback could not be logged at this time. Please try again later.",
                    color = AvoidRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!isSubmitted) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { scanId?.let { onSubmitNegative(it, feedbackComment) } },
                    enabled = scanId != null && !isSubmitting,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (isSubmitting) "Submitting..." else "Submit",
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private enum class ScanFeedback { THUMBS_UP, THUMBS_DOWN }

// Small tappable icon used for the thumbs up/down feedback controls.
// Unselected shows an outlined icon; tapping fills it in — that swap is the
// only selected-state indicator, so it stays subtle instead of a background badge.
@Composable
private fun FeedbackIconButton(
    outlinedIcon: ImageVector,
    filledIcon: ImageVector,
    contentDescription: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = if (isSelected) filledIcon else outlinedIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

// Centers [content] over this position without letting its measured size count
// towards the parent's size. A Box normally sizes itself to the largest of its
// children, so without this the confetti burst (larger than the thumbs up icon)
// would momentarily grow the row around it and shift neighbouring content.
// It measures the child unconstrained, but reports its own size as (0, 0) and
// places the child offset by minus half its size so it still ends up centered.
@Composable
private fun SizeExcludedOverlay(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val placeable = measurables.firstOrNull()?.measure(Constraints())
        layout(width = 0, height = 0) {
            placeable?.placeRelative(x = -(placeable.width / 2), y = -(placeable.height / 2))
        }
    }
}

// Simple particle-burst animation played around the thumbs up button when tapped.
// Distance is stored in Dp (not raw px) so the burst travels a consistent, visible
// distance across screen densities.
private data class ConfettiParticle(val angle: Float, val distance: Dp, val color: Color)

@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val colors = listOf(PrimaryGreen, WarningAmber, InfoBlue, AvatarOrange, AvatarBlue)
    val particles = remember {
        List(14) { index ->
            ConfettiParticle(
                angle = (index.toFloat() / 14f) * (2f * Math.PI.toFloat()) + Random.nextFloat(),
                distance = 14.dp + 10.dp * Random.nextFloat(),
                color = colors[index % colors.size]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = LinearOutSlowInEasing))
        onFinished()
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        // Particles travel outward for the whole burst and only start fading in the
        // back half, so they read clearly once they've cleared the icon instead of
        // fading out at the same rate they're moving away from it.
        val fadeStart = 0.4f
        val alpha = 1f - ((t - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        particles.forEach { particle ->
            val dist = particle.distance.toPx() * t
            val position = Offset(
                x = center.x + cos(particle.angle) * dist,
                y = center.y + sin(particle.angle) * dist,
            )
            drawCircle(
                color = particle.color,
                radius = 3.dp.toPx(),
                center = position,
                alpha = alpha,
            )
        }
    }
}

// Tab 2: Shows the alternatives for the product
@Composable
private fun AlternativesTab(
    alternatives: List<AlternativeProduct>,
    profileName: String,
    errorMessage: String? = null
) {
    Column {
        Text("Safe alternatives for $profileName", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        if (!errorMessage.isNullOrBlank()) {
            Text(
                errorMessage,
                color = AvoidRed
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Display a message if there are no alternatives
        if (alternatives.isEmpty()) {
            Text(
                "No alternatives available yet.",
                color = TextSecondary
            )
        }

        // Alternatives are already filtered as safer options; no SAFE/WARNING/UNSAFE badges.
        alternatives.forEach { alternative ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .padding(14.dp),
            ) {
                Text(
                    text = alternative.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (alternative.brand.isNotEmpty()) {
                    Text(
                        text = alternative.brand,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
