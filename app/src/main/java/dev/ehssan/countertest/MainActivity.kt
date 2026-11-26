package dev.ehssan.countertest

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("counter", MODE_PRIVATE)
        var count = prefs.getInt("count", 0)

        val label = TextView(this).apply {
            text = count.toString()
            textSize = 80f
            gravity = Gravity.CENTER
        }

        val plus = Button(this).apply {
            text = "+1"
            textSize = 32f
            setOnClickListener {
                count++
                label.text = count.toString()
                prefs.edit().putInt("count", count).apply()
            }
        }

        val reset = Button(this).apply {
            text = "تصفير"
            setOnClickListener {
                count = 0
                label.text = "0"
                prefs.edit().putInt("count", 0).apply()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(label)
            addView(plus, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300))
            addView(reset)
        }

        setContentView(root)
    }
}
