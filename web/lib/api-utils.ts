/**
 * API エラーハンドリング用ユーティリティ
 */

import { NextResponse } from 'next/server';
import { ApiError } from './types';

/**
 * 統一されたエラーレスポンスを生成
 * @param message エラーメッセージ
 * @param statusCode HTTPステータスコード
 * @returns NextResponse
 */
export function createErrorResponse(
  message: string,
  statusCode: number = 500
): NextResponse<ApiError> {
  const error: ApiError = {
    error: getErrorType(statusCode),
    message,
    statusCode,
  };

  return NextResponse.json(error, { status: statusCode });
}

/**
 * ステータスコードからエラータイプを取得
 * @param statusCode HTTPステータスコード
 * @returns エラータイプ文字列
 */
function getErrorType(statusCode: number): string {
  switch (statusCode) {
    case 400:
      return 'Bad Request';
    case 404:
      return 'Not Found';
    case 500:
      return 'Internal Server Error';
    default:
      return 'Error';
  }
}

/**
 * クエリパラメータを安全に取得
 * @param searchParams URLSearchParams
 * @param key パラメータ名
 * @returns パラメータ値（存在しない場合は null）
 */
export function getQueryParam(
  searchParams: URLSearchParams,
  key: string
): string | null {
  return searchParams.get(key);
}

/**
 * 数値型のクエリパラメータを取得
 * @param searchParams URLSearchParams
 * @param key パラメータ名
 * @returns 数値（パースできない場合は null）
 */
export function getNumberParam(
  searchParams: URLSearchParams,
  key: string
): number | null {
  const value = searchParams.get(key);
  if (!value) return null;
  
  const num = parseInt(value, 10);
  return isNaN(num) ? null : num;
}

/**
 * BIGINT（文字列）型のクエリパラメータをバリデーション
 * @param searchParams URLSearchParams
 * @param key パラメータ名
 * @returns 文字列（数値として妥当でない場合は null）
 */
export function getBigIntParam(
  searchParams: URLSearchParams,
  key: string
): string | null {
  const value = searchParams.get(key);
  if (!value) return null;
  
  // 数値として妥当かチェック
  if (!/^\d+$/.test(value)) return null;
  
  return value;
}
