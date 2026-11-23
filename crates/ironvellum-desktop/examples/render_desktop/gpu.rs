use egui_wgpu::{Renderer, RendererOptions, ScreenDescriptor};
use std::{sync::mpsc, time::Duration};

pub const FORMAT: wgpu::TextureFormat = wgpu::TextureFormat::Rgba8Unorm;
pub struct Gpu {
    pub device: wgpu::Device,
    queue: wgpu::Queue,
    pub info: wgpu::AdapterInfo,
}
impl Gpu {
    pub fn new() -> Self {
        let instance = wgpu::Instance::default();
        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            compatible_surface: None,
            ..Default::default()
        }))
        .expect("a real offscreen adapter is required");
        let info = adapter.get_info();
        let (device, queue) =
            pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor::default()))
                .expect("create an offscreen device");
        Self {
            device,
            queue,
            info,
        }
    }
    pub fn renderer(&self) -> Renderer {
        Renderer::new(&self.device, FORMAT, RendererOptions::default())
    }
    pub fn draw(
        &self,
        renderer: &mut Renderer,
        ctx: &egui::Context,
        out: egui::FullOutput,
        size: [u32; 2],
        readback: bool,
    ) -> Option<Vec<u8>> {
        let screen = ScreenDescriptor {
            size_in_pixels: size,
            pixels_per_point: out.pixels_per_point,
        };
        let meshes = ctx.tessellate(out.shapes, out.pixels_per_point);
        for (id, delta) in &out.textures_delta.set {
            renderer.update_texture(&self.device, &self.queue, *id, delta);
        }
        let extent = wgpu::Extent3d {
            width: size[0],
            height: size[1],
            depth_or_array_layers: 1,
        };
        let texture = self.device.create_texture(&wgpu::TextureDescriptor {
            label: Some("desktop-offscreen-color"),
            size: extent,
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: FORMAT,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
            view_formats: &[],
        });
        let view = texture.create_view(&Default::default());
        let mut encoder = self.device.create_command_encoder(&Default::default());
        let callbacks =
            renderer.update_buffers(&self.device, &self.queue, &mut encoder, &meshes, &screen);
        {
            let pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("Ironvellum shared desktop view"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &view,
                    resolve_target: None,
                    depth_slice: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color {
                            r: 1.,
                            g: 0.,
                            b: 1.,
                            a: 1.,
                        }),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                ..Default::default()
            });
            renderer.render(&mut pass.forget_lifetime(), &meshes, &screen);
        }
        let row = size[0] * 4;
        let stride =
            row.div_ceil(wgpu::COPY_BYTES_PER_ROW_ALIGNMENT) * wgpu::COPY_BYTES_PER_ROW_ALIGNMENT;
        let buffer = readback.then(|| {
            let b = self.device.create_buffer(&wgpu::BufferDescriptor {
                label: Some("desktop-readback"),
                size: u64::from(stride) * u64::from(size[1]),
                usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
                mapped_at_creation: false,
            });
            encoder.copy_texture_to_buffer(
                texture.as_image_copy(),
                wgpu::TexelCopyBufferInfo {
                    buffer: &b,
                    layout: wgpu::TexelCopyBufferLayout {
                        offset: 0,
                        bytes_per_row: Some(stride),
                        rows_per_image: Some(size[1]),
                    },
                },
                extent,
            );
            b
        });
        let submission = self
            .queue
            .submit(callbacks.into_iter().chain(Some(encoder.finish())));
        for id in out.textures_delta.free {
            renderer.free_texture(&id);
        }
        buffer.map(|b| {
            let (tx, rx) = mpsc::channel();
            b.slice(..).map_async(wgpu::MapMode::Read, move |result| {
                let _ = tx.send(result);
            });
            self.device
                .poll(wgpu::PollType::Wait {
                    submission_index: Some(submission),
                    timeout: Some(Duration::from_secs(30)),
                })
                .expect("offscreen submission completed");
            rx.recv_timeout(Duration::from_secs(5))
                .expect("map callback")
                .expect("map texture readback");
            let mut rgba = Vec::with_capacity(row as usize * size[1] as usize);
            {
                let mapped = b.slice(..).get_mapped_range();
                for line in mapped.chunks_exact(stride as usize) {
                    rgba.extend_from_slice(&line[..row as usize]);
                }
            }
            b.unmap();
            rgba
        })
    }
}
