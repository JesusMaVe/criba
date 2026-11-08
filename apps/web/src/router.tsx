import { createRootRoute, createRoute, createRouter, Outlet } from '@tanstack/react-router';
import { IndexPage } from './routes/index';
import { BoardPage } from './routes/board';

const rootRoute = createRootRoute({
  component: () => (
    <div className="app">
      <Outlet />
    </div>
  ),
});

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: IndexPage,
});

const boardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/boards/$boardId',
  component: BoardPage,
});

const routeTree = rootRoute.addChildren([indexRoute, boardRoute]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
