/* eslint-disable no-var */
import {Instance} from './udStream'

interface Module {
  arguments: string[];
  canvas: HTMLElement | undefined;
  noInitialRun: boolean;
  mainScriptUrlOrBlob: string;
  preRun: (() => void)[];
  setStatusLast: { text: string; };

  setStatus(msg: string): void;

  onRegisterUICallbacks: () => void;

  ccall(ident: string, returnType?: string, argTypes?: string[], args?: any[]): number | void;

  addFunction(callback: function, signature: string): number;

  removeFunction(callbackPtr: number): void;

  _malloc(size: number): number;

  _free(ptr: number): void;
}

interface FS {
  mkdir(path: string): void;

  mount(type: any, opts: any, mountpoint: string): void;
}

export declare global {
  var Module: Module;

  function UTF8ToString(ptr: number, maxBytesToRead?: number): string;

  function getValue(ptr: number, type: string): number;

  var IDBFS: any;
  var FS: FS;
  var instance: Instance;
}
