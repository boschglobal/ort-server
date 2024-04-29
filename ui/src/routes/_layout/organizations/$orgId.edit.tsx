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

import { useOrganizationsServiceGetOrganizationByIdKey, useOrganizationsServicePatchOrganizationById } from '@/api/queries';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { ApiError, OrganizationsService } from '@/api/requests';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from '@/components/ui/card';
import { useSuspenseQuery } from '@tanstack/react-query';
import { useToast } from "@/components/ui/use-toast";
import { ToastError } from "@/components/toast-error";

const formSchema = z.object({
    name: z.string(),
    description: z.string().optional(),
  });

const EditOrganizationPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const { data: organization } = useSuspenseQuery({
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: async () =>
          await OrganizationsService.getOrganizationById(
            Number.parseInt(params.orgId)
          ),
      },  
  );

  const { mutateAsync } = useOrganizationsServicePatchOrganizationById({
    onSuccess() {
      toast({
        title: 'Edit Organization',
        description: 'Organization updated successfully.',
      });
      navigate({
        to: '/organizations/$orgId',
        params: { orgId: params.orgId },
      });
    },
    onError(error: ApiError) {
      toast({
        title: error.message,
        description: <ToastError message={error.body.message} cause={error.body.cause} />,
        variant: 'destructive',
      });
    }
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: organization.name,
      description: organization.description as unknown as string,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      organizationId: organization.id,
      requestBody: {
        name: values.name,
        // There's a bug somewhere in the OpenAPI generation. Swagger UI hints that the bug may be
        // in the API, as description in CreateOrganization is an empty object.
        description: values.description as Record<string, unknown> | undefined,
      },
    });
  }

  return (
    <Card className="w-full max-w-4xl mx-auto">
      <CardHeader>Edit Organization</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
          <CardContent>
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    Enter the name of your organization
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    Optional description of the organization
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button className="m-1" variant="outline" onClick={() => navigate({ to: '/organizations/' + params.orgId })}>
              Cancel
            </Button>
            <Button type="submit">Submit</Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
}

export const Route = createFileRoute('/_layout/organizations/$orgId/edit')({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: () =>
          OrganizationsService.getOrganizationById(
            Number.parseInt(params.orgId)
          ),
    });
  },
  component: EditOrganizationPage,
})