package com.shavlegi.brawlarena.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shavlegi.brawlarena.R

@Composable
fun MenuScreen(
    trophies: Int,
    selectedBrawlerId: String,
    language: String,
    onSelectBrawler: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "BRAWL ARENA",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            stringResource(R.string.tagline),
            color = Color(0xFF7FB0FF),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC93D))
            Spacer(Modifier.width(6.dp))
            Text(
                "${stringResource(R.string.trophies)}: $trophies",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.choose_brawler),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (def in BrawlerDef.ALL) {
                BrawlerCard(
                    def = def,
                    selected = def.id == selectedBrawlerId,
                    onClick = { onSelectBrawler(def.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DFF))
        ) {
            Text(stringResource(R.string.play), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.language), color = Color.White)
            Spacer(Modifier.width(8.dp))
            for ((code, label) in listOf("ka" to "KA", "en" to "EN", "ru" to "RU")) {
                OutlinedButton(
                    onClick = { onSelectLanguage(code) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (language == code) Color(0xFF4C8DFF) else Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Text(label)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121A2B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.how_to_play),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.how_to_text),
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.offline_note),
            color = Color(0xFF888888),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BrawlerCard(
    def: BrawlerDef,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF121A2B), RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFF4C8DFF) else Color(0xFF2A3550),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(Color(def.color), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(def.nameRes).take(1),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(def.nameRes),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Text(
            stringResource(def.descRes),
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.hp_stat, def.hp), color = Color.White, fontSize = 12.sp)
        Text(stringResource(R.string.damage_stat, def.damage), color = Color.White, fontSize = 12.sp)
        Text(stringResource(R.string.range_stat, def.range.toInt()), color = Color.White, fontSize = 12.sp)
        Text(stringResource(R.string.speed_stat, def.speed.toInt()), color = Color.White, fontSize = 12.sp)
    }
}
