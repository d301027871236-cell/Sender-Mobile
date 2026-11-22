package com.sendermobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SenderMobileApp() }
    }
}

@Composable
private fun SenderMobileApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("Sender Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("إدارة العملاء والأرقام", style = MaterialTheme.typography.titleLarge)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تصنيف العملاء حسب الدولة", style = MaterialTheme.typography.titleMedium)
                        CountryLine("🇪🇬", "مصر", "+20")
                        CountryLine("🇰🇼", "الكويت", "+965")
                        CountryLine("🇸🇦", "السعودية", "+966")
                        Text(
                            "يتم التعرف على الدولة من مفتاح الرقم الدولي، مع اعتبار الأرقام المحلية أرقامًا مصرية افتراضيًا.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الخطوة التالية", style = MaterialTheme.typography.titleMedium)
                        Text("استيراد CSV وExcel وVCF، معاينة الأعمدة، كشف التكرار، ثم حفظ العملاء في قاعدة بيانات الهاتف.")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryLine(flag: String, country: String, dialCode: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$flag  $country")
        Text(dialCode, fontWeight = FontWeight.SemiBold)
    }
}
