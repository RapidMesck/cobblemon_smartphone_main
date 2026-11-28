import { useEffect } from 'react'
import { useStudioStore } from '@/store/studioStore'
import { StudioShell } from '@/components/layout/StudioShell'
import { DashboardView } from '@/modules/dashboard/DashboardView'
import { ThemeStudioView } from '@/modules/themes/ThemeStudioView'
import { ScreenStudioView } from '@/modules/screens/ScreenStudioView'
import { ActionStudioView } from '@/modules/actions/ActionStudioView'
import { SkinStudioView } from '@/modules/skins/SkinStudioView'
import { RecipeStudioView } from '@/modules/recipes/RecipeStudioView'
import { CodeStudioView } from '@/modules/code/CodeStudioView'

export default function App() {
  const initialized = useStudioStore((s) => s.initialized)
  const init = useStudioStore((s) => s.init)
  const activeModule = useStudioStore((s) => s.activeModule)

  useEffect(() => {
    void init()
  }, [init])

  if (!initialized) {
    return (
      <div className="flex h-screen items-center justify-center bg-zinc-950 text-sm text-zinc-400">
        Loading Cobblemon Smartphone Studio...
      </div>
    )
  }

  return (
    <StudioShell>
      {activeModule === 'dashboard' && <DashboardView />}
      {activeModule === 'themes' && <ThemeStudioView />}
      {activeModule === 'screens' && <ScreenStudioView />}
      {activeModule === 'actions' && <ActionStudioView />}
      {activeModule === 'skins' && <SkinStudioView />}
      {activeModule === 'recipes' && <RecipeStudioView />}
      {activeModule === 'code' && <CodeStudioView />}
    </StudioShell>
  )
}
