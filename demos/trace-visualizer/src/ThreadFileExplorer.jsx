import React, { useMemo, useState, useCallback } from 'react';
import {
  Upload, FileCode2, Search, Clock3, Hash, FolderTree, 
  ChevronRight, ChevronDown, PanelLeft, PanelLeftClose,
  Layers, Activity, GitCommit, ListOrdered, FileText,
  ArrowRight, ArrowUpRight, PhoneCall, CornerDownRight,
  Filter, X, Eye, MapPin, Copy, Check
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

function findBlockIdsInSource(source) {
  if (!source || typeof source !== 'string') return [];
  return [...source.matchAll(/Executed Block ID:\s*(\d+)/g)].map((m) => Number(m[1]));
}

function highlightSource(source, highlightedBlocks = [], executedBlocks = []) {
  if (!source) return [];
  const highlightSet = new Set(safeArray(highlightedBlocks).map(Number));
  const executedSet = new Set(safeArray(executedBlocks).map(Number));
  
  return source.split('\n').map((line, idx) => {
    const match = line.match(/Executed Block ID:\s*(\d+)/);
    const blockId = match ? Number(match[1]) : null;
    const isHighlighted = blockId !== null && highlightSet.has(blockId);
    const isExecuted = blockId !== null && executedSet.has(blockId);
    
    return (
      <div
        key={idx}
        className={classNames(
          'whitespace-pre-wrap break-words rounded px-2 py-0.5 text-xs font-mono leading-5 transition',
          isHighlighted 
            ? 'bg-amber-500/30 text-amber-100 font-bold ring-1 ring-amber-400' 
            : isExecuted
              ? 'bg-emerald-900/40 text-emerald-200'
              : 'text-slate-500',
          blockId !== null && !isExecuted && 'text-slate-600'
        )}
      >
        {line}
      </div>
    );
  });
}

/* ─────────────────────────── Data Preprocessing ─────────────────────────── */

// Build call relationships within a file from call_tree
function buildFileCallTrees(callTree, filePath, result = []) {
  if (!callTree || callTree.file !== filePath) {
    // If current node is not the target file, continue recursing child calls
    safeArray(callTree?.calls).forEach(child => {
      buildFileCallTrees(child, filePath, result);
    });
    return result;
  }
  
  // It's a method in the current file, build subtree (only includes calls within the same file)
  const localCalls = safeArray(callTree.calls)
    .filter(child => child.file === filePath)
    .map(child => buildLocalCallTree(child, filePath));
  
  result.push({
    method: callTree.method,
    file: callTree.file,
    executed_blocks: safeArray(callTree.executed_blocks),
    source: callTree.source,
    calls: localCalls,
    isRoot: true
  });
  
  // Continue searching for other root nodes (may be called by other files)
  safeArray(callTree.calls).forEach(child => {
    if (child.file !== filePath) {
      buildFileCallTrees(child, filePath, result);
    }
  });
  
  return result;
}

function buildLocalCallTree(node, filePath) {
  const localCalls = safeArray(node.calls)
    .filter(child => child.file === filePath)
    .map(child => buildLocalCallTree(child, filePath));
  
  return {
    method: node.method,
    file: node.file,
    executed_blocks: safeArray(node.executed_blocks),
    source: node.source,
    calls: localCalls
  };
}

// Collect all cross-file calls
function findCrossFileCalls(callTree, filePath, result = [], parentExternal = null) {
  const currentIsExternal = callTree.file !== filePath;
  
  if (!currentIsExternal && parentExternal) {
    // Entry from external file call into current file
    result.push({
      from: parentExternal,
      to: {
        method: callTree.method,
        file: callTree.file,
        executed_blocks: safeArray(callTree.executed_blocks),
        source: callTree.source
      },
      type: 'entry'
    });
  }
  
  if (currentIsExternal && !parentExternal) {
    // Call out from current file
    // Need to find methods of current file in child nodes
    safeArray(callTree.calls).forEach(child => {
      findCrossFileCalls(child, filePath, result, callTree);
    });
    return result;
  }
  
  safeArray(callTree.calls).forEach(child => {
    findCrossFileCalls(child, filePath, result, currentIsExternal ? callTree : parentExternal);
  });
  
  return result;
}

// Find callers of a method in the complete call tree
function findCallersInTree(methodKey, callTree, parent = null, result = []) {
  const currentKey = `${callTree.file}#${callTree.method}`;
  
  if (currentKey === methodKey && parent) {
    result.push(parent);
  }
  
  safeArray(callTree.calls).forEach(child => {
    findCallersInTree(methodKey, child, callTree, result);
  });
  
  return result;
}

/* ─────────────────────────── Subcomponents ─────────────────────────── */

function JsonHintCard() {
  return (
    <div className="mx-auto max-w-3xl rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center shadow-sm">
      <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-700">
        <FolderTree size={30} />
      </div>
      <h2 className="text-2xl font-bold text-slate-900">Load Call Tree Data</h2>
      <p className="mt-3 text-sm leading-6 text-slate-600">
        Upload call_tree_output.json to browse function call relationships within files on a per-thread basis.
      </p>
      <div className="mt-6 rounded-xl bg-slate-50 p-4 text-left">
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
          <FileText size={16} />Expected Structure
        </div>
        <pre className="overflow-x-auto text-xs leading-6 text-slate-600">{`{
  "threads": [{
    "name": "Thread-1",
    "call_tree": {
      "method": "main",
      "file": "com/example/Test.java",
      "calls": [...]
    }
  }]
}`}</pre>
      </div>
    </div>
  );
}

/* ── In-File Call Tree Node ── */
function FileCallNode({ 
  node, 
  depth = 0,
  filePath,
  allBlockIds,
  selectedBlock,
  onSelectBlock,
  onSelectMethod,
  selectedMethod,
  expandedNodes,
  toggleExpanded,
  crossFileEntries,
  crossFileExits
}) {
  const nodeKey = `${node.file}#${node.method}`;
  const isExpanded = expandedNodes.has(nodeKey);
  const isSelected = selectedMethod?.key === nodeKey;
  
  const nodeBlocks = useMemo(() => 
    findBlockIdsInSource(node.source), 
    [node.source]
  );
  
  const executedBlocks = safeArray(node.executed_blocks);
  
  // Check if contains selected block
  const containsSelectedBlock = selectedBlock != null && 
    nodeBlocks.includes(Number(selectedBlock));
  
  // Calculate execution order
  const executionOrder = useMemo(() => {
    const orders = executedBlocks
      .map(bid => allBlockIds.indexOf(bid))
      .filter(pos => pos >= 0)
      .sort((a, b) => a - b);
    if (orders.length === 0) return null;
    return {
      first: orders[0] + 1,
      last: orders[orders.length - 1] + 1
    };
  }, [executedBlocks, allBlockIds]);

  // Find cross-file calls
  const entriesToThis = crossFileEntries.filter(e => 
    e.to.method === node.method
  );
  const exitsFromThis = crossFileExits.filter(e => 
    e.from.method === node.method
  );

  const hasChildren = safeArray(node.calls).length > 0;
  
  return (
    <div className={classNames('relative', depth > 0 && 'ml-6')}>
      {/* Connection line */}
      {depth > 0 && (
        <div className="absolute -left-4 top-4 w-4 h-px bg-slate-300" />
      )}
      
      {/* Node card */}
      <div className={classNames(
        'rounded-lg border p-3 mb-2 transition relative',
        isSelected
          ? 'border-indigo-500 bg-indigo-50 shadow-sm'
          : containsSelectedBlock
            ? 'border-amber-400 bg-amber-50'
            : 'border-slate-200 bg-white hover:border-slate-300'
      )}>
        {/* Cross-file call indicators */}
        {(entriesToThis.length > 0 || exitsFromThis.length > 0) && (
          <div className="absolute -top-2 -right-2 flex gap-1">
            {entriesToThis.length > 0 && (
              <span className="bg-emerald-500 text-white text-[10px] px-1.5 py-0.5 rounded-full" title="Called by external file">
                ↓{entriesToThis.length}
              </span>
            )}
            {exitsFromThis.length > 0 && (
              <span className="bg-blue-500 text-white text-[10px] px-1.5 py-0.5 rounded-full" title="Calls external file">
                ↑{exitsFromThis.length}
              </span>
            )}
          </div>
        )}
        
        <div className="flex items-start gap-2">
          {/* Expand button */}
          {hasChildren ? (
            <button
              onClick={() => toggleExpanded(nodeKey)}
              className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border border-slate-300 bg-slate-50 text-slate-600 hover:bg-slate-100"
            >
              {isExpanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
            </button>
          ) : (
            <div className="mt-0.5 h-5 w-5 shrink-0" />
          )}
          
          <div className="min-w-0 flex-1">
            {/* Method signature */}
            <button
              onClick={() => onSelectMethod({
                key: nodeKey,
                method: node.method,
                file: node.file,
                source: node.source,
                executed_blocks: executedBlocks,
                executionOrder
              })}
              className="text-left w-full"
            >
              <div className="font-mono text-sm font-semibold text-slate-900 line-clamp-2">
                {node.method}
              </div>
              
              {/* Execution info */}
              <div className="mt-1 flex flex-wrap items-center gap-2">
                {executionOrder && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-indigo-100 px-2 py-0.5 text-xs text-indigo-700">
                    <ListOrdered size={10} />
                    #{executionOrder.first}{executionOrder.last !== executionOrder.first && `-${executionOrder.last}`}
                  </span>
                )}
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                  {executedBlocks.length} blocks
                </span>
                {safeArray(node.calls).length > 0 && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                    {safeArray(node.calls).length} internal calls
                  </span>
                )}
              </div>
            </button>
            
            {/* Block ID list */}
            <div className="mt-2 flex flex-wrap gap-1">
              {nodeBlocks.map((bid) => {
                const pos = allBlockIds.indexOf(bid);
                const isExecuted = executedBlocks.includes(bid);
                return (
                  <button
                    key={bid}
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectBlock(selectedBlock === bid ? null : bid);
                    }}
                    className={classNames(
                      'rounded px-1.5 py-0.5 text-[10px] font-mono transition',
                      selectedBlock === bid
                        ? 'bg-amber-500 text-white'
                        : isExecuted
                          ? 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200'
                          : 'bg-slate-100 text-slate-500'
                    )}
                    title={isExecuted ? `Execution order: #${pos + 1}` : 'Not executed'}
                  >
                    #{bid}
                    {isExecuted && pos >= 0 && <span className="ml-0.5 opacity-70">@{pos + 1}</span>}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </div>
      
      {/* Child nodes */}
      <AnimatePresence initial={false}>
        {isExpanded && hasChildren && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden relative"
          >
            {/* Vertical connection line */}
            <div className="absolute left-2 top-0 bottom-4 w-px bg-slate-200" />
            
            {safeArray(node.calls).map((child, idx) => (
              <FileCallNode
                key={`${child.method}-${idx}`}
                node={child}
                depth={depth + 1}
                filePath={filePath}
                allBlockIds={allBlockIds}
                selectedBlock={selectedBlock}
                onSelectBlock={onSelectBlock}
                onSelectMethod={onSelectMethod}
                selectedMethod={selectedMethod}
                expandedNodes={expandedNodes}
                toggleExpanded={toggleExpanded}
                crossFileEntries={crossFileEntries}
                crossFileExits={crossFileExits}
              />
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ── Cross-File Call Panel ── */
function CrossFileCallPanel({ entries, exits, onSelectExternalMethod, allBlockIds }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
      <h4 className="text-sm font-semibold text-slate-700 mb-3 flex items-center gap-2">
        <PhoneCall size={14} />
        Cross-File Call Relationships
      </h4>
      
      {entries.length === 0 && exits.length === 0 ? (
        <p className="text-xs text-slate-500">No cross-file calls</p>
      ) : (
        <div className="space-y-3">
          {/* Entries: called from other files */}
          {entries.length > 0 && (
            <div>
              <div className="text-xs font-medium text-emerald-700 mb-2 flex items-center gap-1">
                <CornerDownRight size={12} />
                Called by External Files ({entries.length})
              </div>
              <div className="space-y-1">
                {entries.map((entry, idx) => (
                  <button
                    key={`entry-${idx}`}
                    onClick={() => onSelectExternalMethod(entry.from)}
                    className="w-full text-left rounded-lg bg-white border border-slate-200 p-2 text-xs hover:border-emerald-300 hover:bg-emerald-50 transition"
                  >
                    <div className="text-slate-500 truncate">{entry.from.file}</div>
                    <div className="font-mono text-slate-700 truncate">{entry.from.method}</div>
                    <div className="mt-1 flex gap-1">
                      {safeArray(entry.from.executed_blocks).slice(0, 3).map(b => (
                        <span key={b} className="text-[10px] bg-slate-100 px-1 rounded">#{b}</span>
                      ))}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}
          
          {/* Exits: calls to other files */}
          {exits.length > 0 && (
            <div>
              <div className="text-xs font-medium text-blue-700 mb-2 flex items-center gap-1">
                <ArrowUpRight size={12} />
                Calls to External Files ({exits.length})
              </div>
              <div className="space-y-1">
                {exits.map((exit, idx) => (
                  <button
                    key={`exit-${idx}`}
                    onClick={() => onSelectExternalMethod(exit.to)}
                    className="w-full text-left rounded-lg bg-white border border-slate-200 p-2 text-xs hover:border-blue-300 hover:bg-blue-50 transition"
                  >
                    <div className="text-slate-500 truncate">{exit.to.file}</div>
                    <div className="font-mono text-slate-700 truncate">{exit.to.method}</div>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/* ── File Card ── */
function FileCard({
  filePath,
  callTrees,
  allBlockIds,
  threadCallTree,
  selectedBlock,
  onSelectBlock,
  onSelectMethod,
  selectedMethod,
  fileIndex,
  isActive,
  onActivate
}) {
  const [expandedNodes, setExpandedNodes] = useState(() => {
    // Expand the first level by default
    const initial = new Set();
    callTrees.forEach((tree, idx) => {
      initial.add(`${tree.file}#${tree.method}`);
    });
    return initial;
  });

  const toggleExpanded = useCallback((key) => {
    setExpandedNodes(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  // Calculate cross-file calls
  const { entries, exits } = useMemo(() => {
    const allEntries = [];
    const allExits = [];
    callTrees.forEach(tree => {
      allEntries.push(...findCrossFileCalls(tree, filePath).filter(c => c.type === 'entry'));
      // Need to traverse the full tree to find exits
    });
    // Simplified: calculate from the full call tree
    const fullEntries = findCrossFileCalls(threadCallTree, filePath).filter(c => c.type === 'entry');
    return { entries: fullEntries, exits: allExits }; // TODO: improve exit detection
  }, [callTrees, filePath, threadCallTree]);

  // Collect all blocks in the file
  const allFileBlocks = useMemo(() => {
    const blocks = new Set();
    const collect = (node) => {
      findBlockIdsInSource(node.source).forEach(b => blocks.add(b));
      safeArray(node.calls).forEach(collect);
    };
    callTrees.forEach(collect);
    return Array.from(blocks);
  }, [callTrees]);

  // Calculate execution coverage
  const coverage = useMemo(() => {
    const executed = allFileBlocks.filter(b => allBlockIds.includes(b));
    const positions = executed
      .map(b => allBlockIds.indexOf(b))
      .filter(p => p >= 0)
      .sort((a, b) => a - b);
    
    return {
      executed: executed.length,
      total: allFileBlocks.length,
      firstPos: positions[0] != null ? positions[0] + 1 : null,
      lastPos: positions[positions.length - 1] != null ? positions[positions.length - 1] + 1 : null
    };
  }, [allFileBlocks, allBlockIds]);

  const containsSelectedBlock = selectedBlock != null && 
    allFileBlocks.includes(Number(selectedBlock));

  if (!isActive) {
    return (
      <button
        onClick={onActivate}
        className={classNames(
          'w-full rounded-xl border p-4 text-left transition flex items-center gap-3',
          containsSelectedBlock ? 'border-amber-400 bg-amber-50' : 'border-slate-200 bg-white hover:border-slate-300'
        )}
      >
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700">
          <FileCode2 size={20} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm font-semibold text-slate-900 truncate">
            {filePath}
          </div>
          <div className="mt-1 flex gap-2 text-xs">
            <span className="text-slate-500">{callTrees.length} root methods</span>
            {coverage.firstPos != null && (
              <span className="text-indigo-600">
                Executed #{coverage.firstPos}-{coverage.lastPos}
              </span>
            )}
          </div>
        </div>
        <ChevronRight size={18} className="text-slate-400 shrink-0" />
      </button>
    );
  }

  return (
    <div className="rounded-2xl border-2 border-indigo-500 bg-white shadow-lg overflow-hidden">
      {/* Header */}
      <div className="bg-indigo-50 px-5 py-4 border-b border-indigo-100">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-600 text-white">
              <FileCode2 size={20} />
            </div>
            <div>
              <div className="font-mono text-sm font-bold text-slate-900">
                {filePath}
              </div>
              <div className="mt-1 flex gap-3 text-xs">
                <span className="text-indigo-700 font-medium">
                  {coverage.executed}/{coverage.total} blocks executed
                </span>
                {coverage.firstPos != null && (
                  <span className="text-slate-600">
                    Sequence: #{coverage.firstPos} ~ #{coverage.lastPos}
                  </span>
                )}
              </div>
            </div>
          </div>
          <button
            onClick={onActivate}
            className="text-slate-400 hover:text-slate-600"
          >
            <X size={20} />
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="p-5 grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left: call tree */}
        <div className="lg:col-span-2 space-y-4">
          <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
            <GitCommit size={14} />
            In-File Call Relationships
          </h4>
          
          <div className="space-y-3">
            {callTrees.map((tree, idx) => (
              <FileCallNode
                key={`root-${idx}`}
                node={tree}
                depth={0}
                filePath={filePath}
                allBlockIds={allBlockIds}
                selectedBlock={selectedBlock}
                onSelectBlock={onSelectBlock}
                onSelectMethod={onSelectMethod}
                selectedMethod={selectedMethod}
                expandedNodes={expandedNodes}
                toggleExpanded={toggleExpanded}
                crossFileEntries={entries}
                crossFileExits={exits}
              />
            ))}
          </div>
        </div>

        {/* Right: cross-file calls & info */}
        <div className="space-y-4">
          <CrossFileCallPanel
            entries={entries}
            exits={exits}
            onSelectExternalMethod={onSelectMethod}
            allBlockIds={allBlockIds}
          />
          
          {/* Quick stats */}
          <div className="rounded-xl border border-slate-200 bg-white p-4">
            <h4 className="text-sm font-semibold text-slate-700 mb-3">Execution Statistics</h4>
            <div className="space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-500">Root methods</span>
                <span className="font-medium">{callTrees.length}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Total methods</span>
                <span className="font-medium">
                  {callTrees.reduce((sum, t) => sum + 1 + countDescendants(t), 0)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Execution coverage</span>
                <span className={classNames(
                  'font-medium',
                  coverage.executed === coverage.total ? 'text-emerald-600' : 'text-amber-600'
                )}>
                  {Math.round(coverage.executed / coverage.total * 100)}%
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function countDescendants(node) {
  return safeArray(node.calls).reduce((sum, child) => 
    sum + 1 + countDescendants(child), 0
  );
}

/* ── Method Detail Panel ── */
function MethodDetailPanel({ method, allBlockIds, onClose }) {
  const [copied, setCopied] = useState(false);

  if (!method) return null;

  const executedBlocks = safeArray(method.executed_blocks);
  const allMethodBlocks = findBlockIdsInSource(method.source);

  const handleCopy = async () => {
    try {
      const prefix = 'Explain the following function in one sentence, and draw its control flow graph in HTML:\n\n';
      const textToCopy = prefix + (method.source || '');
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch { /* ignore */ }
  };
  
  return (
    <div className="fixed inset-y-0 right-0 w-full max-w-2xl bg-white shadow-2xl border-l border-slate-200 z-50 overflow-auto">
      <div className="sticky top-0 bg-white border-b border-slate-200 px-5 py-4 flex items-center justify-between">
        <div>
          <h3 className="font-semibold text-slate-900">Method Details</h3>
          <p className="text-xs text-slate-500 font-mono mt-0.5">{method.file}</p>
        </div>
        <button onClick={onClose} className="p-2 hover:bg-slate-100 rounded-lg">
          <X size={20} className="text-slate-500" />
        </button>
      </div>
      
      <div className="p-5 space-y-5">
        {/* Signature */}
        <div className="rounded-lg bg-slate-50 p-4">
          <div className="text-xs text-slate-500 mb-1">Method Signature</div>
          <div className="font-mono text-sm text-slate-900 break-all">{method.method}</div>
        </div>
        
        {/* Execution info */}
        {method.executionOrder && (
          <div className="flex gap-4">
            <div className="flex-1 rounded-lg bg-indigo-50 p-3">
              <div className="text-xs text-indigo-600 mb-1">First Execution</div>
              <div className="text-lg font-bold text-indigo-900">#{method.executionOrder.first}</div>
            </div>
            <div className="flex-1 rounded-lg bg-indigo-50 p-3">
              <div className="text-xs text-indigo-600 mb-1">Last Execution</div>
              <div className="text-lg font-bold text-indigo-900">#{method.executionOrder.last}</div>
            </div>
          </div>
        )}
        
        {/* Block list */}
        <div>
          <div className="text-sm font-semibold text-slate-700 mb-3">Contained Blocks</div>
          <div className="flex flex-wrap gap-2">
            {allMethodBlocks.map(bid => {
              const isExecuted = executedBlocks.includes(bid);
              const pos = allBlockIds.indexOf(bid);
              return (
                <div
                  key={bid}
                  className={classNames(
                    'rounded-lg px-3 py-2 text-sm font-mono',
                    isExecuted ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-500'
                  )}
                >
                  #{bid}
                  {isExecuted && pos >= 0 && (
                    <span className="ml-2 text-xs opacity-70">Order #{pos + 1}</span>
                  )}
                  {!isExecuted && <span className="ml-2 text-xs">(Not executed)</span>}
                </div>
              );
            })}
          </div>
        </div>
        
        {/* Source code */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <div className="text-sm font-semibold text-slate-700">Trimmed Source Code</div>
            <button
              onClick={handleCopy}
              className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-100 transition"
            >
              {copied ? <Check size={14} className="text-emerald-600" /> : <Copy size={14} />}
              {copied ? 'Copied' : 'Copy And Ask AI'}
            </button>
          </div>
          <div className="rounded-lg border border-slate-700 bg-slate-900 overflow-hidden">
            <div className="bg-slate-800 px-4 py-2 text-xs text-slate-400 font-mono">
              {method.source?.split('\n').length || 0} lines
            </div>
            <div className="max-h-96 overflow-auto p-4">
              {highlightSource(method.source, [], executedBlocks)}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ─────────────────────────── Main Component ─────────────────────────── */

export default function ThreadFileExplorer() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [activeThreadIndex, setActiveThreadIndex] = useState(0);
  const [selectedBlock, setSelectedBlock] = useState(null);
  const [selectedMethod, setSelectedMethod] = useState(null);
  const [activeFile, setActiveFile] = useState(null);
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
      setSelectedMethod(null);
      setActiveFile(null);
    } catch (err) {
      setError(err?.message || 'Parsing failed');
      setData(null);
    }
  }, []);

  const threads = safeArray(data?.threads);
  const activeThread = threads[activeThreadIndex] || null;

  // Organize call tree by file
  const fileBasedData = useMemo(() => {
    if (!activeThread?.call_tree) return [];
    
    // Collect all involved files
    const fileSet = new Set();
    const collectFiles = (node) => {
      if (node?.file) fileSet.add(node.file);
      safeArray(node?.calls).forEach(collectFiles);
    };
    collectFiles(activeThread.call_tree);
    
    // Build call trees for each file
    return Array.from(fileSet).sort().map(filePath => ({
      path: filePath,
      trees: buildFileCallTrees(activeThread.call_tree, filePath)
    })).filter(f => f.trees.length > 0);
  }, [activeThread]);

  const filteredThreads = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    if (!q) return threads;
    return threads.filter((t) =>
      [t?.name, t?.call_tree?.method, t?.call_tree?.file]
        .map((s) => String(s || '').toLowerCase())
        .some((s) => s.includes(q)),
    );
  }, [threads, searchText]);

  const handleSwitchThread = (thread) => {
    const idx = threads.findIndex((t) => t === thread);
    setActiveThreadIndex(idx >= 0 ? idx : 0);
    setSelectedBlock(null);
    setSelectedMethod(null);
    setActiveFile(null);
  };

  // Calculate thread stats
  const stats = useMemo(() => {
    if (!activeThread) return null;
    const trace = safeArray(activeThread.block_trace);
    
    let methodCount = 0;
    let fileCount = 0;
    const countNodes = (node) => {
      methodCount++;
      safeArray(node.calls).forEach(countNodes);
    };
    
    const fileSet = new Set();
    const collectFiles = (node) => {
      if (node?.file) fileSet.add(node.file);
      safeArray(node?.calls).forEach(collectFiles);
    };
    
    if (activeThread.call_tree) {
      countNodes(activeThread.call_tree);
      collectFiles(activeThread.call_tree);
    }
    fileCount = fileSet.size;
    
    return {
      methodCount,
      fileCount,
      blockCount: trace.length
    };
  }, [activeThread]);

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">

      {/* ── Header ── */}
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-screen-2xl flex-col gap-4 px-4 py-4 md:px-6 lg:px-8">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-600 text-white shadow-sm">
                <FolderTree size={24} />
              </div>
              <div>
                <h1 className="text-xl font-bold md:text-2xl">Thread File Explorer</h1>
                <p className="text-sm text-slate-500">Visualization of function call relationships within files</p>
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
                className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-9 pr-3 text-sm outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
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
                <div className={classNames(
                  'flex items-center',
                  sidebarCollapsed ? 'flex-col gap-2' : 'justify-between gap-2 mb-3'
                )}>
                  {!sidebarCollapsed && (
                    <div className="flex items-center gap-2">
                      <Layers size={18} className="text-indigo-600" />
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
                              ? 'bg-indigo-600 text-white'
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
                            isActive ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                          )}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="truncate text-sm font-semibold text-slate-900">
                                {thread?.name || '(unnamed thread)'}
                              </div>
                              <div className="mt-1 text-xs text-slate-500">order #{thread?.order ?? '-'}</div>
                            </div>
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
                          <Activity size={20} className="text-indigo-600" />
                          <h2 className="text-2xl font-bold text-slate-900">{activeThread.name || 'Unnamed Thread'}</h2>
                        </div>
                        <div className="mt-2 text-sm text-slate-500">Execution order #{activeThread.order ?? '-'}</div>
                      </div>
                      {selectedBlock != null && (
                        <button
                          onClick={() => setSelectedBlock(null)}
                          className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800 hover:bg-amber-100 transition flex items-center gap-2"
                        >
                          <span>Selected Block: <span className="font-mono font-semibold">#{selectedBlock}</span></span>
                          <X size={14} />
                        </button>
                      )}
                    </div>
                    
                    {stats && (
                      <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-3">
                        <div className="rounded-xl bg-indigo-50 p-4">
                          <div className="text-xs text-indigo-600 font-medium uppercase">Files</div>
                          <div className="text-2xl font-bold text-indigo-900">{stats.fileCount}</div>
                        </div>
                        <div className="rounded-xl bg-indigo-50 p-4">
                          <div className="text-xs text-indigo-600 font-medium uppercase">Methods</div>
                          <div className="text-2xl font-bold text-indigo-900">{stats.methodCount}</div>
                        </div>
                        <div className="rounded-xl bg-indigo-50 p-4">
                          <div className="text-xs text-indigo-600 font-medium uppercase">Executed Blocks</div>
                          <div className="text-2xl font-bold text-indigo-900">{stats.blockCount}</div>
                        </div>
                      </div>
                    )}
                  </div>

                  {/* File list */}
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <h3 className="text-lg font-semibold text-slate-900 flex items-center gap-2">
                        <FolderTree size={18} className="text-indigo-600" />
                        File Execution Sequence
                        <span className="text-sm font-normal text-slate-500">({fileBasedData.length} files)</span>
                      </h3>
                      <span className="text-sm text-slate-500">
                        {activeFile ? 'Click the file card to view details, click again to close' : 'Click a file to view internal call relationships'}
                      </span>
                    </div>
                    
                    {fileBasedData.map((fileData, idx) => (
                      <FileCard
                        key={fileData.path}
                        filePath={fileData.path}
                        callTrees={fileData.trees}
                        allBlockIds={safeArray(activeThread.block_trace)}
                        threadCallTree={activeThread.call_tree}
                        selectedBlock={selectedBlock}
                        onSelectBlock={setSelectedBlock}
                        onSelectMethod={setSelectedMethod}
                        selectedMethod={selectedMethod}
                        fileIndex={idx}
                        isActive={activeFile === fileData.path}
                        onActivate={() => setActiveFile(activeFile === fileData.path ? null : fileData.path)}
                      />
                    ))}
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

      {/* Method detail drawer */}
      <AnimatePresence>
        {selectedMethod && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/20 z-40"
              onClick={() => setSelectedMethod(null)}
            />
            <motion.div
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', damping: 30, stiffness: 300 }}
              className="fixed inset-y-0 right-0 z-50 w-full max-w-2xl"
            >
              <MethodDetailPanel
                method={selectedMethod}
                allBlockIds={safeArray(activeThread?.block_trace)}
                onClose={() => setSelectedMethod(null)}
              />
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}