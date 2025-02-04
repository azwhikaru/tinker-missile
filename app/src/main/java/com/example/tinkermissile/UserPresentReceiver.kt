package com.example.tinkermissile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
        if(intent?.action == Intent.ACTION_USER_PRESENT) {
            val mainIntent = Intent(ctx, MainActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            ctx?.startActivity(mainIntent)
        }
    }
}