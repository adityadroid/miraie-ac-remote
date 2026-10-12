package dev.adityadroid.acremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    val canSubmit = user.isNotBlank() && pass.isNotEmpty() && !busy
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Palette.Text,
        unfocusedTextColor = Palette.Text,
        focusedContainerColor = Palette.Tile,
        unfocusedContainerColor = Palette.Tile,
        focusedBorderColor = Palette.Accent.copy(alpha = 0.6f),
        unfocusedBorderColor = Palette.Border,
        focusedLabelColor = Palette.Accent,
        unfocusedLabelColor = Palette.Secondary,
        cursorColor = Palette.Accent,
    )

    Column(modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AcUnit, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("MirAIe AC", color = Palette.Text, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Text(
            "Sign in with your Panasonic MirAIe account.",
            color = Palette.Secondary, fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        )
        Spacer(Modifier.height(2.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Mobile number or email") },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onLogin(user, pass) }),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (show) "Hide password" else "Show password",
                        tint = Palette.Secondary,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(error, color = Palette.Error, fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Button(
            onClick = { onLogin(user, pass) },
            enabled = canSubmit,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Accent,
                contentColor = Color(0xFF1A1712),
                disabledContainerColor = Palette.Tile,
                disabledContentColor = Palette.Secondary,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(color = Palette.Secondary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text("Sign in", fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}
