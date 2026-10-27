import type {Edge,Node} from 'reactflow';
export type NodeStatus='PENDING'|'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELED'|'BLOCKED'|'STALE';
export interface CanvasNodeData{name:string;config:Record<string,unknown>;status?:NodeStatus;progress?:number;instanceCount?:number}
export interface WorkflowCanvas{schemaVersion:string;viewport:{x:number;y:number;zoom:number};nodes:Node<CanvasNodeData>[];edges:Edge[]}
export interface Project{id:string;name:string;description?:string;dramaType:string;artStyle:string;episodeCount:number;updatedAt:string}
export interface Asset{id:string;projectId:string;name:string;assetType:string;logicalKey:string;versionNo:number;contentStatus:string;selected:boolean;locked:boolean;contentText?:string;storageKey?:string;createdAt:string}
export interface Run{id:string;projectId:string;workflowId:string;status:string;progress:number;createdAt:string;startedAt?:string;finishedAt?:string}
