# ジョブ（backend）

`bot/RaidNotificationScheduler.kt`。Spring の `@Scheduled` は使わず、`ScheduledThreadPool(4)` で 1 件ずつ予約する。

| ジョブ | 予約のタイミング | 処理 |
|---|---|---|
| 開始通知 `scheduleNotification` | レイド作成時（`notifyTime`） | 通知 Embed と報告ボタンを送信し、`markNotificationSent` で `notifyMessageId` を保存。ギルドごとに 1 件 |
| 自動終了 `scheduleAutoEnd` | レイド作成時（`endTime`） | `finishRaid` で終了し `aggregateRaidResults` の集計を送信。失敗したら `rescheduleAutoEndRetry` で再試行 |
| 終了後の片付け `cleanupAfterRaidEnd` | レイド終了時 | 通知と自動終了の予約を取り消し、報告ボタンを無効化（`disableReportButton`、`notifyMessageId` から取り直す） |
| 再開 `resumeSchedules` | ボット起動時（`DiscordBot`） | `findNotifiableActiveUnionRaids` と `findUnfinishedUnionRaids` から予約を作り直す |

- 予約はメモリ上にしか無いので、再起動後は `resumeSchedules` だけが頼り。通知メッセージは `notifyMessageId` から取り直す。
- delay=0 で予約すると、登録より先にタスクが動く競合があるため、コード内のコメントに従う。
