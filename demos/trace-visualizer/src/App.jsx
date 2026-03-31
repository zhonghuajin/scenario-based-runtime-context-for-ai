import React, { useState } from 'react';
import { GitBranch, FolderTree } from 'lucide-react';
import CallTreeVisualizer from './CallTreeVisualizer';
import ThreadFileExplorer from './ThreadFileExplorer';

export default function App() {
  const [activeView, setActiveView] = useState('calltree'); // 'calltree' | 'fileexplorer'

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Global Navigation Bar */}
      <div className="fixed top-0 left-0 right-0 z-50 bg-slate-900 text-white shadow-lg">
        <div className="mx-auto max-w-screen-2xl px-4 py-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-emerald-500">
                <span className="text-lg font-bold">RT</span>
              </div>
              <div>
                <h1 className="font-bold text-lg">Runtime Context Visualizer</h1>
                <p className="text-xs text-slate-400">Runtime Context Visualization Toolset</p>
              </div>
            </div>

            {/* View Toggle Buttons */}
            <div className="flex items-center gap-2 bg-slate-800 rounded-lg p-1">
              <button
                onClick={() => setActiveView('calltree')}
                className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition ${
                  activeView === 'calltree'
                    ? 'bg-blue-600 text-white shadow'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700'
                }`}
              >
                <GitBranch size={16} />
                Call Tree View
              </button>
              <button
                onClick={() => setActiveView('fileexplorer')}
                className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition ${
                  activeView === 'fileexplorer'
                    ? 'bg-emerald-600 text-white shadow'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700'
                }`}
              >
                <FolderTree size={16} />
                File Explorer View
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content Area - Add top spacing for navigation bar */}
      <div className="pt-16">
        {activeView === 'calltree' ? <CallTreeVisualizer /> : <ThreadFileExplorer />}
      </div>
    </div>
  );
}
