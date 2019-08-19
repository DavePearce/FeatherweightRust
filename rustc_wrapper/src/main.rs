#![feature(rustc_private)]

extern crate rustc;
extern crate rustc_codegen_utils;
extern crate rustc_driver;
extern crate rustc_errors;
extern crate rustc_interface;
extern crate rustc_metadata;
extern crate syntax;
use rustc::session::config::ErrorOutputType;
use rustc::session::early_error;
use rustc_driver::{run_compiler, Callbacks};
use rustc_interface::interface;

fn main() {
    let args: Vec<_> = std::env::args().collect();
    rustc_driver::run_compiler(&args, &mut Calls, None, None);
}

struct Calls;

impl Callbacks for Calls {
    fn config(&mut self, config: &mut interface::Config) {
    }
}
