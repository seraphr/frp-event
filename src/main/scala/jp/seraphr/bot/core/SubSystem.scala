package jp.seraphr.bot.core

import jp.seraphr.bot.chat.ChatSystemComponent
import java.io.BufferedWriter
import java.io.BufferedReader

trait SubSystemComponent extends BotSystemComponent {
  self: ChatSystemComponent =>

  trait SubSystem {
    def init(): Unit
    def serialize(aWriter: BufferedWriter): Unit
    def deserialize(aReader: BufferedReader): Unit
    def terminate(): Unit
  }
}