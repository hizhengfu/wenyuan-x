import * as compilerSfc from 'vue/compiler-sfc';

export function createIsolatedTemplateCompiler(): typeof compilerSfc {
  // Discard previously parsed descriptors once when the Vite compiler is initialized.
  compilerSfc.parseCache.clear();

  return {
    ...compilerSfc,
    compileTemplate(options) {
      // Template transforms mutate an AST supplied by the shared SFC descriptor.
      // Reparse through the public compiler API so script import usage sees the original tree.
      const { ast: _sharedAst, ...freshParseOptions } = options;
      return compilerSfc.compileTemplate(freshParseOptions);
    },
  };
}
