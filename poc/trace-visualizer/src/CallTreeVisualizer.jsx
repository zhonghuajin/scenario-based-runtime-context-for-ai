import React, { useMemo, useState, useCallback } from 'react';
import {
  Upload, ChevronRight, ChevronDown, FileCode2, Activity,
  Code2, GitCommit, Layers, Search, ListTree, Clock3,
  Braces, Hash, FolderTree, Info, Copy, Check, PanelLeft, PanelLeftClose,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

/* ─────────────────────────── Utility Functions ─────────────────────────── */

function classNames(...items) {
  return items.filter(Boolean).join(' ');
}
function safeArray(value) {
  return Array.isArray(value) ? value : [];
}
function formatCount(value) {
  return typeof value === 'number' ? value.toLocaleString() : '0';
}
function readFileAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => resolve(String(e.target?.result || ''));
    reader.onerror = () => reject(new Error('File read failed'));
    reader.readAsText(file, 'utf-8');
  });
}
function collectStatsFromNode(node, stats = { methods: 0, files: new Set(), blocks: 0 }) {
  if (!node || typeof node !== 'object') return stats;
  stats.methods += 1;
  if (node.file) stats.files.add(node.file);
  stats.blocks += safeArray(node.executed_blocks).length;
  safeArray(node.calls).forEach((child) => collectStatsFromNode(child, stats));
  return stats;
}
function flattenNodes(node, list = []) {
  if (!node || typeof node !== 'object') return list;
  list.push(node);
  safeArray(node.calls).forEach((child) => flattenNodes(child, list));
  return list;
}
function findBlockIdsInSource(source) {
  if (!source || typeof source !== 'string') return [];
  return [...source.matchAll(/Executed Block ID:\s*(\d+)/g)].map((m) => Number(m[1]));
}
function highlightSource(source, highlightedBlocks = []) {
  if (!source) return [];
  const targetSet = new Set(highlightedBlocks.map(Number));
  return source.split('\n').map((line, idx) => {
    const match = line.match(/Executed Block ID:\s*(\d+)/);
    const blockId = match ? Number(match[1]) : null;
    const active = blockId !== null && targetSet.has(blockId);
    return (
      <div
        key={idx}
        className={classNames(
          'whitespace-pre-wrap break-words rounded px-2 py-0.5',
          active ? 'bg-emerald-900/40 text-emerald-200' : 'text-slate-300',
        )}
      >
        {line}
      </div>
    );
  });
}

/* ─────────── Depth → Tailwind margin-left mapping (eliminate inline styles) ─────────── */
const DEPTH_ML = [
  'ml-0', 'ml-4', 'ml-8', 'ml-12', 'ml-16',
  'ml-20', 'ml-24', 'ml-28', 'ml-32', 'ml-36',
];
function depthMargin(depth) {
  return DEPTH_ML[Math.min(depth, DEPTH_ML.length - 1)];
}

/* ─────────────────────────── Subcomponents ─────────────────────────── */

function JsonHintCard() {
  return (
    <div className="mx-auto max-w-3xl rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center shadow-sm">
      <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-100 text-blue-700">
        <Layers size={30} />
      </div>
      <h2 className="text-2xl font-bold text-slate-900">Load Execution Trace JSON</h2>
      <p className="mt-3 text-sm leading-6 text-slate-600">
        After uploading a data file that matches this structure, the tool will automatically parse threads, execution order, call tree, trimmed source code, and executed block information.
      </p>
      <div className="mt-6 rounded-xl bg-slate-50 p-4 text-left">
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
          <Info size={16} />Expected Structure
        </div>
        <pre className="overflow-x-auto text-xs leading-6 text-slate-600">{`{
  "threads": [
    {
      "name": "Thread-1",
      "order": 1,
      "block_trace": [32, 33, 34],
      "call_tree": {
        "method": "...",
        "file": "...",
        "executed_blocks": [32],
        "source": "...",
        "calls": []
      }
    }
  ]
}`}</pre>
      </div>
    </div>
  );
}

function SummaryCard({ icon, label, value, sub }) {
  const Icon = icon;
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
          <Icon size={20} />
        </div>
        <div className="min-w-0">
          <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
          <div className="mt-1 text-2xl font-bold text-slate-900">{value}</div>
          {sub && <div className="mt-1 text-xs text-slate-500">{sub}</div>}
        </div>
      </div>
    </div>
  );
}

function BlockTraceView({ blockTrace, selectedBlock, onSelectBlock }) {
  const trace = safeArray(blockTrace);
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center gap-2">
        <Clock3 className="text-blue-600" size={18} />
        <h3 className="text-lg font-semibold text-slate-900">Thread Execution Trace</h3>
      </div>
      {trace.length === 0 ? (
        <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">No block_trace data</div>
      ) : (
        <div className="max-h-72 overflow-auto rounded-xl border border-slate-200 bg-slate-50 p-3">
          <div className="flex flex-wrap gap-2">
            {trace.map((blockId, idx) => {
              const active = Number(selectedBlock) === Number(blockId);
              return (
                <button
                  key={`${blockId}-${idx}`}
                  onClick={() => onSelectBlock(active ? null : blockId)}
                  title={`Step ${idx + 1}`}
                  className={classNames(
                    'inline-flex items-center rounded-full border px-3 py-1.5 text-xs font-mono transition',
                    active
                      ? 'border-blue-600 bg-blue-600 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:border-blue-400 hover:text-blue-700',
                  )}
                >
                  {blockId}
                  {idx < trace.length - 1 && <span className="ml-2 text-slate-400">→</span>}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function SourcePanel({ node, selectedBlock }) {
  const [copied, setCopied] = useState(false);

  const blockIdsInSource = useMemo(() => findBlockIdsInSource(node?.source), [node?.source]);
  const highlightedBlocks = useMemo(() => {
    if (selectedBlock == null) return safeArray(node?.executed_blocks);
    const num = Number(selectedBlock);
    return blockIdsInSource.includes(num) ? [num] : safeArray(node?.executed_blocks);
  }, [selectedBlock, node?.executed_blocks, blockIdsInSource]);

  const handleCopy = async () => {
    try {
      const prefix = 'Explain the following function in one sentence, and draw a control flow graph using HTML:\n\n';
      const textToCopy = prefix + (node?.source || '');
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch { /* ignore */ }
  };

  if (!node?.source) {
    return (
      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">
        No source code content for the current node
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-slate-700 bg-slate-900 shadow-sm">
      <div className="flex items-center justify-between border-b border-slate-700 bg-slate-800 px-4 py-2">
        <div className="flex items-center gap-2 text-sm text-slate-200">
          <Code2 size={16} />
          <span className="font-mono">Trimmed Source Code</span>
        </div>
        <button
          onClick={handleCopy}
          className="inline-flex items-center gap-1 rounded-md border border-slate-600 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-700"
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
          {copied ? 'Copied' : 'Copy And Ask AI'}
        </button>
      </div>
      <div className="max-h-screen overflow-auto p-4 text-xs font-mono leading-6">
        <pre>{highlightSource(node.source, highlightedBlocks)}</pre>
      </div>
    </div>
  );
}

/* ── NodeMeta: Improved ── Display full function name when space is sufficient, intelligently truncate when compact ── */
function NodeMeta({ node, isSelected, onSelect, selectedBlock, compact = false }) {
  const executedBlocks = safeArray(node.executed_blocks);
  const childCount = safeArray(node.calls).length;
  const blockMatch =
    selectedBlock != null &&
    (executedBlocks.includes(Number(selectedBlock)) ||
      findBlockIdsInSource(node.source).includes(Number(selectedBlock)));

  return (
    <button
      onClick={onSelect}
      className={classNames(
        'w-full rounded-xl border p-3 text-left transition',
        isSelected
          ? 'border-blue-500 bg-blue-50 shadow-sm'
          : blockMatch
            ? 'border-emerald-400 bg-emerald-50'
            : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
      )}
    >
      {/* Method signature line */}
      <div className={classNames('flex items-start gap-2', compact ? 'flex-col' : 'justify-between')}>
        <div className={classNames(compact ? 'w-full' : 'min-w-0 flex-1')}>
          <div
            className={classNames(
              'font-mono text-sm font-semibold text-slate-900',
              compact ? 'line-clamp-2' : 'truncate'
            )}
            title={node.method || '(unknown method)'}
          >
            {node.method || '(unknown method)'}
          </div>
          <div
            className="mt-1 flex items-center gap-1 text-xs text-slate-500"
            title={node.file || '(unknown file)'}
          >
            <FileCode2 size={12} className="shrink-0" />
            <span className={compact ? 'line-clamp-1' : 'truncate'}>{node.file || '(unknown file)'}</span>
          </div>
        </div>

        {/* Statistics badges */}
        <div className={classNames(
          'flex shrink-0 gap-1',
          compact ? 'flex-row' : 'flex-col items-end'
        )}>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
            {executedBlocks.length} blocks
          </span>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
            {childCount} calls
          </span>
        </div>
      </div>

      {/* Block ID badges */}
      {executedBlocks.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {executedBlocks.map((id) => (
            <span
              key={id}
              className={classNames(
                'rounded-full px-2 py-0.5 text-xs font-mono',
                Number(selectedBlock) === Number(id)
                  ? 'bg-blue-600 text-white'
                  : 'bg-emerald-100 text-emerald-800',
              )}
            >
              #{id}
            </span>
          ))}
        </div>
      )}
    </button>
  );
}

function CallTreeNode({ node, depth = 0, selectedNode, setSelectedNode, selectedBlock, compact = false }) {
  const [expanded, setExpanded] = useState(depth < 2);
  const children = safeArray(node?.calls);
  const hasChildren = children.length > 0;
  const isSelected = selectedNode === node;

  return (
    <div className="mt-2">
      <div className={classNames('flex items-start gap-2', depthMargin(depth))}>
        <button
          onClick={() => hasChildren && setExpanded((v) => !v)}
          className={classNames(
            'mt-3 flex h-7 w-7 shrink-0 items-center justify-center rounded-md border transition',
            hasChildren
              ? 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
              : 'cursor-default border-transparent text-transparent',
          )}
        >
          {hasChildren && (expanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />)}
        </button>
        <div className="min-w-0 flex-1">
          <NodeMeta
            node={node}
            isSelected={isSelected}
            selectedBlock={selectedBlock}
            onSelect={() => setSelectedNode(node)}
            compact={compact}
          />
        </div>
      </div>

      <AnimatePresence initial={false}>
        {expanded && hasChildren && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.18 }}
            className="overflow-hidden"
          >
            {children.map((child, idx) => (
              <CallTreeNode
                key={`${child.method || 'node'}-${idx}-${depth + 1}`}
                node={child}
                depth={depth + 1}
                selectedNode={selectedNode}
                setSelectedNode={setSelectedNode}
                selectedBlock={selectedBlock}
                compact={compact}
              />
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ─────────────────────────── Main Component ─────────────────────────── */

export default function CallTreeVisualizer() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [activeThreadIndex, setActiveThreadIndex] = useState(0);
  const [selectedNode, setSelectedNode] = useState(null);
  const [selectedBlock, setSelectedBlock] = useState(null);
  const [searchText, setSearchText] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const handleFileUpload = useCallback(async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const text = await readFileAsText(file);
      const json = JSON.parse(text);
      if (!json || !Array.isArray(json.threads)) {
        throw new Error('Invalid JSON structure: missing threads array');
      }
      setData(json);
      setError('');
      setActiveThreadIndex(0);
      setSelectedBlock(null);
      setSelectedNode(json.threads?.[0]?.call_tree || null);
    } catch (err) {
      setError(err?.message || 'Parsing failed');
      setData(null);
      setSelectedNode(null);
    }
  }, []);

  const threads = safeArray(data?.threads);
  const activeThread = threads[activeThreadIndex] || null;

  const filteredThreads = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    if (!q) return threads;
    return threads.filter((t) =>
      [t?.name, t?.call_tree?.method, t?.call_tree?.file]
        .map((s) => String(s || '').toLowerCase())
        .some((s) => s.includes(q)),
    );
  }, [threads, searchText]);

  const activeThreadStats = useMemo(() => {
    if (!activeThread?.call_tree) {
      return { methods: 0, fileCount: 0, blockCount: 0, traceCount: safeArray(activeThread?.block_trace).length };
    }
    const s = collectStatsFromNode(activeThread.call_tree);
    return { methods: s.methods, fileCount: s.files.size, blockCount: s.blocks, traceCount: safeArray(activeThread?.block_trace).length };
  }, [activeThread]);

  const allNodesInThread = useMemo(() =>
    activeThread?.call_tree ? flattenNodes(activeThread.call_tree) : [],
    [activeThread],
  );

  const matchedNodes = useMemo(() => {
    if (selectedBlock == null) return [];
    const target = Number(selectedBlock);
    return allNodesInThread.filter((n) =>
      safeArray(n.executed_blocks).includes(target) ||
      findBlockIdsInSource(n.source).includes(target),
    );
  }, [allNodesInThread, selectedBlock]);

  const handleSwitchThread = (thread) => {
    const idx = threads.findIndex((t) => t === thread);
    setActiveThreadIndex(idx >= 0 ? idx : 0);
    setSelectedBlock(null);
    setSelectedNode(thread?.call_tree || null);
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">

      {/* ── Header ── */}
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-screen-2xl flex-col gap-4 px-4 py-4 md:px-6 lg:px-8">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-sm">
                <Layers size={24} />
              </div>
              <div>
                <h1 className="text-xl font-bold md:text-2xl">Execution Trace Visualizer</h1>
                <p className="text-sm text-slate-500">A general-purpose visualization tool for threads, call trees, code blocks, and trimmed source code</p>
              </div>
            </div>
            <label className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50">
              <Upload size={18} />Select JSON File
              <input type="file" accept=".json,application/json" className="hidden" onChange={handleFileUpload} />
            </label>
          </div>

          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="relative w-full md:max-w-md">
              <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder="Search thread name / method name / file name"
                className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-9 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
            {data
              ? <div className="text-sm text-slate-500">Loaded <span className="font-semibold text-slate-700">{formatCount(threads.length)}</span> threads</div>
              : <div className="text-sm text-slate-400">No data loaded yet</div>
            }
          </div>
        </div>
      </header>

      {/* ── Main ── */}
      <main className="mx-auto max-w-screen-2xl px-4 py-6 md:px-6 lg:px-8">
        {error && (
          <div className="mb-6 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            Parsing failed: {error}
          </div>
        )}

        {!data ? <JsonHintCard /> : (
          <div className="flex flex-col gap-6 lg:flex-row">

            {/* ── Thread List Sidebar ── */}
            <aside className={classNames(
              'shrink-0 transition-all duration-300 ease-in-out',
              sidebarCollapsed ? 'lg:w-12' : 'lg:w-72'
            )}>
              <div className={classNames(
                'sticky top-28 rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden',
                sidebarCollapsed ? 'p-2' : 'p-4'
              )}>
                {/* Header: title + collapse button */}
                <div className={classNames(
                  'flex items-center',
                  sidebarCollapsed ? 'flex-col gap-2' : 'justify-between gap-2 mb-3'
                )}>
                  {!sidebarCollapsed && (
                    <div className="flex items-center gap-2">
                      <ListTree size={18} className="text-blue-600" />
                      <h2 className="text-base font-semibold text-slate-900">Thread List</h2>
                    </div>
                  )}
                  <button
                    onClick={() => setSidebarCollapsed(v => !v)}
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-700 transition"
                    title={sidebarCollapsed ? 'Expand thread list' : 'Collapse thread list'}
                  >
                    {sidebarCollapsed ? <PanelLeft size={18} /> : <PanelLeftClose size={18} />}
                  </button>
                </div>

                {/* Collapsed state: show only icon indicators for threads */}
                {sidebarCollapsed ? (
                  <div className="flex flex-col items-center gap-2">
                    {filteredThreads.map((thread, idx) => {
                      const isActive = activeThread === thread;
                      return (
                        <button
                          key={`${thread?.name || 'thread'}-${idx}`}
                          onClick={() => handleSwitchThread(thread)}
                          className={classNames(
                            'flex h-8 w-8 items-center justify-center rounded-lg text-xs font-medium transition',
                            isActive
                              ? 'bg-blue-600 text-white'
                              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                          )}
                          title={`${thread?.name || '(unnamed thread)'} - order #${thread?.order ?? '-'}`}
                        >
                          {thread?.order ?? idx + 1}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  /* Expanded state: full list */
                  <div className="max-h-[70vh] space-y-2 overflow-auto pr-1">
                    {filteredThreads.length === 0 ? (
                      <div className="rounded-xl bg-slate-50 p-3 text-sm text-slate-500">No matching threads</div>
                    ) : filteredThreads.map((thread, idx) => {
                      const isActive = activeThread === thread;
                      return (
                        <button
                          key={`${thread?.name || 'thread'}-${idx}`}
                          onClick={() => handleSwitchThread(thread)}
                          className={classNames(
                            'w-full rounded-xl border p-3 text-left transition',
                            isActive ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                          )}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="truncate text-sm font-semibold text-slate-900">
                                {thread?.name || '(unnamed thread)'}
                              </div>
                              <div className="mt-1 text-xs text-slate-500">order #{thread?.order ?? '-'}</div>
                            </div>
                            <span className="shrink-0 rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-700">
                              {safeArray(thread?.block_trace).length} blocks
                            </span>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </aside>

            {/* ── Right Main Area ── */}
            <section className="min-w-0 flex-1 space-y-6">
              {activeThread ? (
                <>
                  {/* Overview */}
                  <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
                      <div>
                        <div className="flex items-center gap-2">
                          <Activity size={20} className="text-blue-600" />
                          <h2 className="text-2xl font-bold text-slate-900">{activeThread.name || 'Unnamed Thread'}</h2>
                        </div>
                        <div className="mt-2 text-sm text-slate-500">Order #{activeThread.order ?? '-'}</div>
                      </div>
                      {selectedBlock != null && (
                        <div className="rounded-xl bg-blue-50 px-3 py-2 text-sm text-blue-800">
                          Currently Selected Block: <span className="font-mono font-semibold">#{selectedBlock}</span>
                        </div>
                      )}
                    </div>
                    <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                      <SummaryCard icon={ListTree}   label="Methods"          value={formatCount(activeThreadStats.methods)}    sub="Total call tree nodes" />
                      <SummaryCard icon={FolderTree} label="Files"            value={formatCount(activeThreadStats.fileCount)}  sub="Number of involved files" />
                      <SummaryCard icon={GitCommit}  label="Executed Blocks"  value={formatCount(activeThreadStats.blockCount)} sub="Total executed_blocks count" />
                      <SummaryCard icon={Hash}       label="Block Trace"      value={formatCount(activeThreadStats.traceCount)} sub="Thread execution sequence length" />
                    </div>
                  </div>

                  {/* Execution trace */}
                  <BlockTraceView
                    blockTrace={activeThread.block_trace}
                    selectedBlock={selectedBlock}
                    onSelectBlock={setSelectedBlock}
                  />

                  {/* ── Call Tree + Node Details ── */}
                  <div className="grid grid-cols-1 gap-6 xl:grid-cols-5">

                    {/* Call tree */}
                    <div className="xl:col-span-2 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                      <div className="mb-4 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Braces size={18} className="text-blue-600" />
                          <h3 className="text-lg font-semibold text-slate-900">Call Tree</h3>
                        </div>
                        <span className="text-xs text-slate-400">Click a node to view source code</span>
                      </div>
                      {activeThread.call_tree ? (
                        <div className="max-h-screen overflow-auto pr-1">
                          <CallTreeNode
                            node={activeThread.call_tree}
                            selectedNode={selectedNode}
                            setSelectedNode={setSelectedNode}
                            selectedBlock={selectedBlock}
                            compact={false}
                          />
                        </div>
                      ) : (
                        <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">This thread has no call_tree data</div>
                      )}
                    </div>

                    {/* Node details */}
                    <div className="xl:col-span-3 space-y-4">
                      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                        <div className="mb-4 flex items-center gap-2">
                          <FileCode2 size={18} className="text-blue-600" />
                          <h3 className="text-lg font-semibold text-slate-900">Node Details</h3>
                        </div>

                        {selectedNode ? (
                          <div className="space-y-4">
                            <div>
                              <div className="text-xs uppercase tracking-wide text-slate-500">Method</div>
                              <div className="mt-1 font-mono text-sm font-semibold text-slate-900 break-all">
                                {selectedNode.method || '(unknown method)'}
                              </div>
                            </div>
                            <div>
                              <div className="text-xs uppercase tracking-wide text-slate-500">File</div>
                              <div className="mt-1 break-all text-sm text-slate-700">
                                {selectedNode.file || '(unknown file)'}
                              </div>
                            </div>
                            <div>
                              <div className="text-xs uppercase tracking-wide text-slate-500">Executed Blocks</div>
                              <div className="mt-2 flex flex-wrap gap-2">
                                {safeArray(selectedNode.executed_blocks).length > 0
                                  ? safeArray(selectedNode.executed_blocks).map((id) => (
                                    <button
                                      key={id}
                                      onClick={() => setSelectedBlock(id)}
                                      className={classNames(
                                        'rounded-full px-2.5 py-1 text-xs font-mono transition',
                                        Number(selectedBlock) === Number(id)
                                          ? 'bg-blue-600 text-white'
                                          : 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200',
                                      )}
                                    >
                                      #{id}
                                    </button>
                                  ))
                                  : <span className="text-sm text-slate-500">None</span>
                                }
                              </div>
                            </div>
                            <div>
                              <div className="text-xs uppercase tracking-wide text-slate-500">Child Calls</div>
                              <div className="mt-1 text-sm text-slate-700">{safeArray(selectedNode.calls).length}</div>
                            </div>
                            <SourcePanel node={selectedNode} selectedBlock={selectedBlock} />
                          </div>
                        ) : (
                          <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Please select a node from the call tree on the left</div>
                        )}
                      </div>

                      {/* Block matched nodes */}
                      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                        <div className="mb-4 flex items-center gap-2">
                          <GitCommit size={18} className="text-blue-600" />
                          <h3 className="text-lg font-semibold text-slate-900">Block Matched Nodes</h3>
                        </div>
                        {selectedBlock != null ? (
                          matchedNodes.length > 0 ? (
                            <div className="space-y-3">
                              {matchedNodes.map((node, idx) => (
                                <button
                                  key={`${node.method || 'matched'}-${idx}`}
                                  onClick={() => setSelectedNode(node)}
                                  className="w-full rounded-xl border border-slate-200 bg-slate-50 p-3 text-left transition hover:border-slate-300 hover:bg-white"
                                >
                                  <div
                                    className="truncate font-mono text-sm font-semibold text-slate-900"
                                    title={node.method || '(unknown method)'}
                                  >
                                    {node.method || '(unknown method)'}
                                  </div>
                                  <div className="mt-1 truncate text-xs text-slate-500" title={node.file}>
                                    {node.file || '(unknown file)'}
                                  </div>
                                </button>
                              ))}
                            </div>
                          ) : (
                            <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
                              No nodes matching this block were found in the current thread
                            </div>
                          )
                        ) : (
                          <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
                            Select a block from the execution trace or node details to view associated call nodes
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </>
              ) : (
                <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
                  No threads available to display
                </div>
              )}
            </section>
          </div>
        )}
      </main>
    </div>
  );
}