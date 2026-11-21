package com.shavlegi.brawlarena.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shavlegi.brawlarena.R

data class MatchResult(
    val placement: Int,
    val kills: Int,
    val trophyDelta: Int,
    val totalTrophies: Int,
    val victory: Boolean
)

@Composable
fun ResultScreen(
    result: MatchResult,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(if (result.victory) R.string.victory else R.string.defeat),
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = if (result.victory) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.placement, result.placement),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${stringResource(R.string.kills)}: ${result.kills}",
            color = Color.White,
            fontSize = 18.sp
        )
        val deltaText = if (result.trophyDelta >= 0) "+${result.trophyDelta}" else "${result.trophyDelta}"
        Text(
            "${stringResource(R.string.trophies)}: $deltaText",
            color = if (result.trophyDelta >= 0) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "${stringResource(R.string.trophies)}: ${result.totalTrophies}",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DFF))
        ) {
            Text(stringResource(R.string.play_again), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onMenu,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(stringResource(R.string.back_to_menu), fontSize = 18.sp)
        }
    }
}
