"use client";

import { useEffect, useState } from "react";

/** 終了時刻を過ぎたら「進行中」表示が切り替わるよう、現在時刻を定期的に更新する */
export function useNow(intervalMs = 30_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
