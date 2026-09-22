import { getSession } from "@/lib/session";

export default async function RaidsPage() {
  const session = await getSession();

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-950">
      <main className="flex flex-col items-center gap-8 px-8 py-16 text-center">
        <div className="space-y-4">
          <h1 className="text-5xl font-bold text-slate-900 dark:text-slate-50 tracking-tight">
            レイド一覧
          </h1>
        </div>

        <div className="mt-8 max-w-2xl space-y-4">
          <p className="text-lg text-slate-600 dark:text-slate-400">
            ログイン成功！🎉
          </p>
          {session.userId && (
            <div className="text-base text-slate-500 dark:text-slate-500">
              <p>ユーザーID: {session.userId}</p>
              <p>所属ギルド数: {session.guildIds?.length || 0}</p>
              {session.guildIds && session.guildIds.length > 0 && (
                <div className="mt-4">
                  <p className="font-semibold">ギルドID一覧:</p>
                  <ul className="text-sm">
                    {session.guildIds.map((gid) => (
                      <li key={gid}>{gid}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>

        <form action="/api/auth/logout" method="POST">
          <button
            type="submit"
            className="mt-8 px-6 py-3 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-lg shadow-lg transition-colors"
          >
            ログアウト
          </button>
        </form>
      </main>
    </div>
  );
}
