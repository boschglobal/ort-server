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

import { 
  useRepositoriesServiceGetRepositoryById, 
  useRepositoriesServiceGetRepositoryByIdKey, 
  useRepositoriesServicePatchRepositoryById } from '@/api/queries';
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { ApiError, RepositoriesService } from '@/api/requests';
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
import { CreateRepository } from '@/api/requests';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { useSuspenseQuery } from '@tanstack/react-query';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useToast } from "@/components/ui/use-toast";
import { ToastError } from "@/components/toast-error";

const formSchema = z.object({
  url: z.string(),
  type: z.nativeEnum(CreateRepository.type),
});

const EditRepositoryPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const { data: repository } = useSuspenseQuery({
    queryKey: [useRepositoriesServiceGetRepositoryById, params.orgId, params.productId, params.repoId],
    queryFn: async () =>
      await RepositoriesService.getRepositoryById(
        Number.parseInt(params.repoId)
      ),
    },  
  );

  const { mutateAsync } = useRepositoriesServicePatchRepositoryById({
    onSuccess() {
      toast({
        title: 'Edit repository',
        description: 'Repository updated successfully.',
      });
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId',
        params: { orgId: params.orgId, productId: params.productId, repoId: params.repoId},
      });
    },
    onError(error: ApiError) {
      toast({
        title: 'Edit repository - FAILURE',
        description: <ToastError message={`${error.message}: ${error.body.message}`} cause={error.body.cause} />,
        variant: 'destructive',
      });
    },
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      url: repository.url,
      type: repository.type,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      repositoryId: repository.id,
      requestBody: {
        url: values.url,
        type: values.type,
      },
    });
  }

  return (
    <Card className="w-full max-w-4xl mx-auto">
      <CardHeader>
        <CardTitle>Edit repository</CardTitle>
      </CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
          <CardContent>
            <FormField
              control={form.control}
              name="url"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>URL</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>URL of the repository</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="type"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    defaultValue={field.value}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Select a type" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {Object.keys(CreateRepository.type).map((type) => (
                        <SelectItem key={type} value={type}>
                          {type}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormDescription>Type of the repository</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button className="m-1" variant="outline" onClick={() => navigate({ to: '/organizations/' + params.orgId + '/products/' + params.productId })}>
              Cancel
            </Button>
            <Button type="submit">Submit</Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
}

export const Route = createFileRoute('/_layout/organizations/$orgId/products/$productId/repositories/$repoId/edit')({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
        queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
        queryFn: () =>
          RepositoriesService.getRepositoryById(Number.parseInt(params.repoId)),
    });
  },
  component: EditRepositoryPage,
})