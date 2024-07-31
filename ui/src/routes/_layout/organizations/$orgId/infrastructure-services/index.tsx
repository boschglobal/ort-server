/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import { useSuspenseQueries } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import {
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Pencil } from 'lucide-react';

import {
  useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId,
  useOrganizationsServiceGetOrganizationByIdKey,
} from '@/api/queries';
import {
  InfrastructureService,
  InfrastructureServicesService,
  OrganizationsService,
} from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const InfrastructureServices = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: organization }, { data: infraServices }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: async () =>
          await OrganizationsService.getOrganizationById({
            organizationId: Number.parseInt(params.orgId),
          }),
      },
      {
        queryKey: [
          useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId,
          params.orgId,
          pageIndex,
          pageSize,
        ],
        queryFn: () =>
          InfrastructureServicesService.getInfrastructureServicesByOrganizationId(
            {
              organizationId: Number.parseInt(params.orgId),
              limit: pageSize,
              offset: pageIndex * pageSize,
            }
          ),
      },
    ],
  });

  const columns: ColumnDef<InfrastructureService>[] = [
    {
      accessorKey: 'name',
      header: 'Name',
    },
    {
      accessorKey: 'description',
      header: 'Description',
    },
    {
      accessorKey: 'url',
      header: 'URL',
    },
    {
      accessorKey: 'usernameSecretRef',
      header: 'Username Secret',
      cell: ({ row }) => (
        <div className='flex items-baseline'>
          {row.original.usernameSecretRef}{' '}
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Link
                  to='/organizations/$orgId/secrets/$secretName/edit'
                  params={{
                    orgId: params.orgId,
                    secretName: row.original.usernameSecretRef,
                  }}
                  className='px-2'
                >
                  <span className='sr-only'>Edit</span>
                  <Pencil size={16} className='inline' />
                </Link>
              </TooltipTrigger>
              <TooltipContent>
                Edit the secret "{row.original.usernameSecretRef}"
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      ),
    },
    {
      accessorKey: 'passwordSecretRef',
      header: 'Password Secret',
      cell: ({ row }) => (
        <div className='flex items-baseline'>
          {row.original.passwordSecretRef}{' '}
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Link
                  to='/organizations/$orgId/secrets/$secretName/edit'
                  params={{
                    orgId: params.orgId,
                    secretName: row.original.passwordSecretRef,
                  }}
                  className='px-2'
                >
                  <span className='sr-only'>Edit</span>
                  <Pencil size={16} className='inline' />
                </Link>
              </TooltipTrigger>
              <TooltipContent>
                Edit the secret "{row.original.passwordSecretRef}"
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      ),
    },
    {
      accessorKey: 'credentialsTypes',
      header: 'Credentials Included In Files',
      cell: ({ row }) => {
        const inFiles = row.original.credentialsTypes?.map((type) => {
          if (type === 'NETRC_FILE') return 'Netrc File';
          if (type === 'GIT_CREDENTIALS_FILE') return 'Git Credentials File';
        });

        return inFiles?.join(', ');
      },
    },
  ];

  const table = useReactTable({
    data: infraServices?.data || [],
    columns,
    pageCount: Math.ceil(infraServices.pagination.totalCount / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  return (
    <Card className='mx-auto w-full max-w-7xl'>
      <CardHeader>
        <CardTitle>Infrastructure Services</CardTitle>
        <CardDescription>
          Manage infrastructure services for {organization.name}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTable table={table} />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/infrastructure-services/'
)({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: () =>
          OrganizationsService.getOrganizationById({
            organizationId: Number.parseInt(params.orgId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [
          useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId,
          params.orgId,
          page,
          pageSize,
        ],
        queryFn: () =>
          InfrastructureServicesService.getInfrastructureServicesByOrganizationId(
            {
              organizationId: Number.parseInt(params.orgId),
              limit: pageSize || defaultPageSize,
              offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
            }
          ),
      }),
    ]);
  },
  component: InfrastructureServices,
  pendingComponent: LoadingIndicator,
});